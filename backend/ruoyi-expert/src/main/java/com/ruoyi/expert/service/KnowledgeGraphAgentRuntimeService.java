package com.ruoyi.expert.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.config.ExpertAgentProperties;
import com.ruoyi.expert.domain.KnowledgeGraphAgentRunRequest;
import com.ruoyi.expert.mapper.AgentRuntimeMapper;

/**
 * 有界 GraphRAG Agent。模型只能调用白名单只读工具，后端持久化工具轨迹。
 */
@Service
public class KnowledgeGraphAgentRuntimeService
{
    private static final Logger log =
            LoggerFactory.getLogger(KnowledgeGraphAgentRuntimeService.class);

    private static final String SYSTEM_PROMPT = """
            你是科技保险专家匹配 Agent。你的目标是根据用户任务，从知识图谱中寻找可解释的专家候选。
            你只能使用提供的只读工具，不得编造专家、关系或证据。
            推荐流程：
            1. 使用 kg_search_experts 按任务关键词检索候选专家。
            2. 使用 expert_filter_available 对本次检索到的全部候选执行硬门禁。
            3. 对门禁通过的候选使用 kg_find_paths 获取能力、产业、项目等路径。
            4. 使用 kg_expand_evidence 获取材料和档案证据。
            5. hardGateEligible=false 的专家必须直接排除，不能进入正式排序。
            6. 输出简洁的候选排序、知识图谱路径、画像分、证据、风险/缺口。
            PENDING 或 REJECTED 关系不能作为正式匹配依据。
            如果证据不足，请明确说明，不要补全。
            当信息足够时直接输出最终文本，不再调用工具。
            """;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Autowired
    private AgentRuntimeMapper runMapper;

    @Autowired
    private KnowledgeGraphAgentTools tools;

    @Autowired
    private Neo4jHttpClient neo4j;

    @Autowired
    private ExpertAgentProperties properties;

    @Autowired
    private KnowledgeGraphTaskService taskGraphService;

    public Map<String, Object> run(String objective, int requestedMaxSteps, String createBy)
    {
        return run(objective, requestedMaxSteps, createBy, DemandContext.empty());
    }

    public Map<String, Object> run(KnowledgeGraphAgentRunRequest request, String createBy)
    {
        return run(request.getObjective(),
                request.getMaxSteps() == null ? 6 : request.getMaxSteps(),
                createBy,
                new DemandContext(request.getServiceDate(), request.getServiceRegion(),
                        request.getServiceMode(), request.getIndustry(),
                        request.getRequiredCapabilities()));
    }

    private Map<String, Object> run(String objective, int requestedMaxSteps, String createBy,
            DemandContext demand)
    {
        if (StringUtils.isEmpty(objective))
        {
            throw new ServiceException("Agent 任务目标不能为空");
        }
        if (!neo4j.isEnabled())
        {
            throw new ServiceException("Neo4j 未启用，无法运行 GraphRAG Agent");
        }
        int maxSteps = Math.max(1, Math.min(requestedMaxSteps, 10));
        String runId = UUID.randomUUID().toString();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runId", runId);
        run.put("objective", objective);
        run.put("model", properties.isRemoteConfigured() ? properties.getModel() : "rule-fallback");
        run.put("maxSteps", maxSteps);
        run.put("createBy", createBy);
        runMapper.insertRun(run);

        int[] stepCounter = {0};
        String finalAnswer;
        String warning = null;
        Map<String, Object> taskProjection = Map.of();
        try
        {
            List<String> taskTerms = tools.suggestTerms(objective);
            taskProjection = taskGraphService.createTask(runId, objective,
                    demand.serviceDate(), demand.serviceRegion(), demand.serviceMode(),
                    demand.industry(), demand.requiredCapabilities(), taskTerms);
        }
        catch (Exception ex)
        {
            warning = "任务图谱投影失败，继续执行候选检索：" + ex.getMessage();
            log.warn("任务图谱投影失败，runId={}，error={}", runId, ex.getMessage());
        }
        String runStatus = "COMPLETED";
        try
        {
            if (properties.isRemoteConfigured())
            {
                try
                {
                    finalAnswer = runWithModel(objective, demand, maxSteps, runId, stepCounter);
                }
                catch (Exception ex)
                {
                    warning = "模型调用失败，已回退本地工具流程：" + ex.getMessage();
                    log.warn("GraphRAG Agent 模型调用失败，runId={}，error={}",
                            runId, ex.getMessage());
                    finalAnswer = runFallback(objective, demand, maxSteps, runId, stepCounter);
                }
            }
            else
            {
                finalAnswer = runFallback(objective, demand, maxSteps, runId, stepCounter);
            }
            runMapper.updateRun(runId, runStatus, stepCounter[0], finalAnswer, warning);
            Map<String, Object> result = result(runId, runStatus, finalAnswer, warning,
                    stepCounter[0]);
            result.putAll(taskProjection);
            return result;
        }
        catch (Exception ex)
        {
            runMapper.updateRun(runId, "FAILED", stepCounter[0], null, ex.getMessage());
            throw new ServiceException("Agent 运行失败：" + ex.getMessage());
        }
    }

    public Map<String, Object> getRun(String runId)
    {
        Map<String, Object> run = runMapper.selectRun(runId);
        if (run == null)
        {
            throw new ServiceException("Agent 运行记录不存在");
        }
        run.put("steps", runMapper.selectSteps(runId));
        return run;
    }

    private String runWithModel(String objective, DemandContext demand, int maxSteps, String runId,
            int[] stepCounter)
    {
        JSONArray messages = new JSONArray();
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", demand.withObjective(objective)));
        while (stepCounter[0] < maxSteps)
        {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", properties.getModel());
            requestBody.put("messages", messages);
            requestBody.put("tools", tools.definitions());
            requestBody.put("tool_choice", "auto");
            requestBody.put("temperature", 0.1);
            requestBody.put("max_tokens", 5000);
            JSONObject response = callModel(requestBody);
            JSONArray choices = response.getJSONArray("choices");
            if (choices == null || choices.isEmpty())
            {
                throw new ServiceException("模型响应缺少 choices");
            }
            JSONObject responseMessage = choices.getJSONObject(0).getJSONObject("message");
            if (responseMessage == null)
            {
                throw new ServiceException("模型响应缺少 message");
            }
            JSONArray toolCalls = responseMessage.getJSONArray("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty())
            {
                String content = responseMessage.getString("content");
                if (StringUtils.isEmpty(content))
                {
                    throw new ServiceException("模型没有返回最终答案");
                }
                return content;
            }
            messages.add(responseMessage);
            for (int index = 0; index < toolCalls.size()
                    && stepCounter[0] < maxSteps; index++)
            {
                JSONObject toolCall = toolCalls.getJSONObject(index);
                JSONObject function = toolCall.getJSONObject("function");
                String toolName = function == null ? null : function.getString("name");
                JSONObject arguments = parseArguments(
                        function == null ? null : function.getString("arguments"));
                stepCounter[0]++;
                JSONObject observation = executeTool(runId, stepCounter[0],
                        toolName, arguments);
                JSONObject toolMessage = message("tool", observation.toJSONString());
                toolMessage.put("tool_call_id", toolCall.getString("id"));
                messages.add(toolMessage);
            }
        }
        JSONObject finalPrompt = message("user",
                "工具调用预算已用完。请不要再调用工具，基于现有工具观察直接输出最终候选答案、"
                        + "证据、风险和仍需人工确认的事项。");
        messages.add(finalPrompt);
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", properties.getModel());
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.1);
        requestBody.put("max_tokens", 3000);
        JSONObject response = callModel(requestBody);
        JSONArray choices = response.getJSONArray("choices");
        if (choices == null || choices.isEmpty()
                || choices.getJSONObject(0).getJSONObject("message") == null
                || StringUtils.isEmpty(choices.getJSONObject(0)
                        .getJSONObject("message").getString("content")))
        {
            throw new ServiceException("工具预算耗尽后模型未返回最终答案");
        }
        return choices.getJSONObject(0).getJSONObject("message").getString("content");
    }

    private String runFallback(String objective, DemandContext demand, int maxSteps, String runId,
            int[] stepCounter)
    {
        if (stepCounter[0] >= maxSteps)
        {
            return "Agent 工具调用预算已用完。图谱检索和证据已记录，请查看运行轨迹后人工确认候选专家。";
        }
        Set<String> termSet = new LinkedHashSet<>(demand.explicitTerms());
        termSet.addAll(tools.suggestTerms(objective));
        List<String> terms = termSet.stream().limit(8).toList();
        if (terms.isEmpty())
        {
            terms = List.of(objective.length() > 60
                    ? objective.substring(0, 60) : objective);
        }
        stepCounter[0]++;
        JSONObject searchArgs = new JSONObject();
        searchArgs.put("terms", terms);
        searchArgs.put("limit", 8);
        JSONObject search = executeTool(runId, stepCounter[0],
                "kg_search_experts", searchArgs);
        JSONArray candidates = search.getJSONArray("candidates");
        if (candidates == null || candidates.isEmpty())
        {
            return "未在已审核知识图谱中找到匹配专家。请补充能力、产业、区域或项目关键词后重试。";
        }
        if (stepCounter[0] >= maxSteps - 1)
        {
            return "工具预算不足以完成可用性硬门禁，请增加 maxSteps 后重试。";
        }

        Map<String, JSONObject> candidateByKey = new LinkedHashMap<>();
        List<String> candidateKeys = new ArrayList<>();
        List<Integer> matchCounts = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++)
        {
            JSONObject candidate = candidates.getJSONObject(index);
            candidateByKey.put(candidate.getString("externalKey"), candidate);
            candidateKeys.add(candidate.getString("externalKey"));
            matchCounts.add(candidate.getIntValue("matchCount"));
        }
        JSONObject gateArguments = new JSONObject();
        gateArguments.put("expertExternalKeys", candidateKeys);
        gateArguments.put("matchCounts", matchCounts);
        gateArguments.put("serviceDate", demand.serviceDate());
        gateArguments.put("serviceRegion", demand.serviceRegion());
        gateArguments.put("serviceMode", demand.serviceMode());
        gateArguments.put("industry", demand.industry());
        stepCounter[0]++;
        JSONObject gateResult = executeTool(runId, stepCounter[0],
                "expert_filter_available", gateArguments);
        JSONArray availableCandidates = gateResult.getJSONArray("candidates");
        List<JSONObject> availability = new ArrayList<>();
        if (availableCandidates != null)
        {
            for (int index = 0; index < availableCandidates.size(); index++)
            {
                availability.add(availableCandidates.getJSONObject(index));
            }
        }

        List<JSONObject> rankedCandidates = new ArrayList<>();
        availability.stream()
                .filter(item -> item.getBooleanValue("hardGateEligible"))
                .sorted((left, right) -> Double.compare(
                        right.getDoubleValue("rankingScore"),
                        left.getDoubleValue("rankingScore")))
                .forEach(item -> {
                    JSONObject candidate = candidateByKey.get(item.getString("expertExternalKey"));
                    if (candidate != null)
                    {
                        candidate.put("hardGateEligible", true);
                        candidate.put("rankingScore", item.getDoubleValue("rankingScore"));
                        rankedCandidates.add(candidate);
                    }
                });
        List<JSONObject> selected = rankedCandidates;
        if (selected.isEmpty())
        {
            return "候选专家均未通过认证、接单状态、服务区域或时间表硬门禁。"
                    + "系统未将不可用人员纳入正式候选。";
        }
        if (selected.size() > 2)
        {
            selected = new ArrayList<>(selected.subList(0, 2));
        }

        Map<String, JSONObject> pathsByExpert = new HashMap<>();
        for (JSONObject candidate : selected)
        {
            if (stepCounter[0] >= maxSteps - 1)
            {
                break;
            }
            JSONObject args = new JSONObject();
            args.put("expertExternalKey", candidate.getString("externalKey"));
            args.put("terms", terms);
            args.put("limit", 12);
            stepCounter[0]++;
            pathsByExpert.put(candidate.getString("externalKey"),
                    executeTool(runId, stepCounter[0], "kg_find_paths", args));
        }

        if (stepCounter[0] < maxSteps && !pathsByExpert.isEmpty())
        {
            Set<Long> relationIds = new LinkedHashSet<>();
            for (JSONObject pathResult : pathsByExpert.values())
            {
                JSONArray paths = pathResult.getJSONArray("paths");
                if (paths == null)
                {
                    continue;
                }
                for (int index = 0; index < paths.size(); index++)
                {
                    JSONArray relations = paths.getJSONObject(index).getJSONArray("relations");
                    if (relations != null)
                    {
                        for (int relation = 0; relation < relations.size(); relation++)
                        {
                            Long id = relations.getJSONObject(relation).getLong("relationId");
                            if (id != null)
                            {
                                relationIds.add(id);
                            }
                        }
                    }
                }
            }
            JSONObject evidenceArgs = new JSONObject();
            evidenceArgs.put("relationIds", relationIds);
            evidenceArgs.put("entityIds", List.of());
            stepCounter[0]++;
            executeTool(runId, stepCounter[0], "kg_expand_evidence", evidenceArgs);
        }
        return fallbackAnswer(selected, pathsByExpert, availability);
    }

    private static JSONObject availabilityArgs(JSONObject candidate, DemandContext demand)
    {
        JSONObject args = new JSONObject();
        args.put("expertExternalKey", candidate.getString("externalKey"));
        args.put("serviceDate", demand.serviceDate());
        args.put("serviceRegion", demand.serviceRegion());
        args.put("serviceMode", demand.serviceMode());
        args.put("industry", demand.industry());
        args.put("matchCount", candidate.getIntValue("matchCount"));
        return args;
    }

    private JSONObject executeTool(String runId, int stepNo, String toolName,
            JSONObject arguments)
    {
        long started = System.nanoTime();
        JSONObject observation;
        String status = "SUCCESS";
        try
        {
            observation = tools.execute(toolName, arguments);
        }
        catch (Exception ex)
        {
            status = "FAILED";
            observation = new JSONObject();
            observation.put("error", ex.getMessage());
        }
        long latency = Duration.ofNanos(System.nanoTime() - started).toMillis();
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("runId", runId);
        step.put("stepNo", stepNo);
        step.put("toolName", toolName);
        step.put("toolInputJson", arguments.toJSONString());
        step.put("observationJson", observation.toJSONString());
        step.put("stepStatus", status);
        step.put("latencyMs", latency);
        runMapper.insertStep(step);
        return observation;
    }

    private JSONObject callModel(JSONObject requestBody)
    {
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl()))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            requestBody.toJSONString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2)
            {
                throw new ServiceException("模型调用失败，状态码：" + response.statusCode()
                        + "：" + abbreviate(response.body(), 1000));
            }
            JSONObject body = JSON.parseObject(response.body());
            if (body == null)
            {
                throw new ServiceException("模型返回为空");
            }
            return body;
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            throw new ServiceException("模型调用被中断");
        }
        catch (ServiceException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new ServiceException("模型调用异常：" + ex.getMessage());
        }
    }

    private static JSONObject parseArguments(String arguments)
    {
        if (StringUtils.isEmpty(arguments))
        {
            return new JSONObject();
        }
        try
        {
            return JSON.parseObject(arguments);
        }
        catch (Exception ex)
        {
            throw new ServiceException("工具参数不是合法 JSON");
        }
    }

    private static JSONObject message(String role, String content)
    {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private static String fallbackAnswer(List<JSONObject> candidates,
            Map<String, JSONObject> pathsByExpert, List<JSONObject> availability)
    {
        StringBuilder answer = new StringBuilder("以下专家已通过可用性硬门禁，并按图谱匹配与画像质量综合排序：\n");
        for (int index = 0; index < candidates.size(); index++)
        {
            JSONObject candidate = candidates.get(index);
            String key = candidate.getString("externalKey");
            JSONObject pathResult = pathsByExpert.get(key);
            JSONObject availabilityResult = availability.stream()
                    .filter(item -> key.equals(item.getString("expertExternalKey")))
                    .findFirst().orElse(new JSONObject());
            answer.append(index + 1).append(". ")
                    .append(candidate.getString("expertName"))
                    .append("（").append(key).append("）")
                    .append("，图谱命中 ").append(candidate.getIntValue("matchCount"))
                    .append(" 项")
                    .append(matchedNames(candidate))
                    .append("，综合排序分 ")
                    .append(availabilityResult.getDoubleValue("rankingScore"));
            if (pathResult != null)
            {
                answer.append("，可用路径 ").append(pathResult.getIntValue("pathCount")).append(" 条");
            }
            answer.append("；认证状态：")
                    .append(availabilityResult.getString("reviewStatus"))
                    .append("，接单状态：")
                    .append(availabilityResult.getString("serviceStatus"))
                    .append("，画像均分：")
                    .append(availabilityResult.getDoubleValue("profileScore"))
                    .append("，画像覆盖：")
                    .append(availabilityResult.getDoubleValue("profileCoverage"))
                    .append("%")
                    .append("。\n");
        }
        answer.append("请结合展开的材料证据进行最终人工确认。");
        return answer.toString();
    }

    private static String matchedNames(JSONObject candidate)
    {
        JSONArray matched = candidate.getJSONArray("matched");
        if (matched == null || matched.isEmpty())
        {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (int index = 0; index < Math.min(3, matched.size()); index++)
        {
            JSONObject item = matched.getJSONObject(index);
            if (item != null && StringUtils.isNotEmpty(item.getString("name")))
            {
                names.add(item.getString("entityType") + ":" + item.getString("name"));
            }
        }
        return names.isEmpty() ? "" : "（" + String.join("、", names) + "）";
    }

    private static Map<String, Object> result(String runId, String status,
            String finalAnswer, String warning, int stepCount)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("status", status);
        result.put("stepCount", stepCount);
        result.put("finalAnswer", finalAnswer);
        result.put("warning", warning);
        return result;
    }

    private record DemandContext(String serviceDate, String serviceRegion,
            String serviceMode, String industry, String requiredCapabilities)
    {
        private static DemandContext empty()
        {
            return new DemandContext(null, null, null, null, null);
        }

        private String withObjective(String objective)
        {
            JSONObject demand = new JSONObject();
            demand.put("objective", objective);
            demand.put("serviceDate", serviceDate);
            demand.put("serviceRegion", serviceRegion);
            demand.put("serviceMode", serviceMode);
            demand.put("industry", industry);
            demand.put("requiredCapabilities", requiredCapabilities);
            return demand.toJSONString();
        }

        private List<String> explicitTerms()
        {
            if (requiredCapabilities == null || requiredCapabilities.isBlank())
            {
                return List.of();
            }
            return java.util.Arrays.stream(requiredCapabilities.split("[,，;；、\\n]"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .limit(12)
                    .toList();
        }
    }

    private static String abbreviate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
