package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertProfileUpdateItemReview;
import com.ruoyi.expert.domain.ExpertProfileUpdateReviewRequest;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestion;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestionItem;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertProfileUpdateMapper;

/**
 * 规则驱动的画像更新建议及人工审核闭环。
 */
@Service
public class ExpertProfileUpdateService
{
    private static final List<String> POSITIVE_TERMS = List.of(
            "优秀", "良好", "满意", "按期", "及时", "完整", "通过", "高质量", "复用");
    private static final List<String> NEGATIVE_TERMS = List.of(
            "投诉", "逾期", "延期", "失联", "违规", "缺失", "不满意", "退回", "失败");

    @Autowired
    private ExpertProfileUpdateMapper updateMapper;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertOpenApiMapper openApiMapper;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    @Autowired
    private KnowledgeGraphQueryService graphQueryService;

    @Autowired
    private ExpertProfileEvidenceAgentService evidenceAgentService;

    @Transactional
    public ExpertProfileUpdateSuggestion generate(ExpertServiceEvidence evidence)
    {
        ExpertProfileUpdateSuggestion existing =
                updateMapper.selectByEvidenceId(evidence.getEvidenceId());
        if (existing != null)
        {
            return existing;
        }
        String content = content(evidence);
        ExpertProfile profile = platformMapper.selectProfileByUserId(evidence.getUserId());
        if (profile == null)
        {
            return null;
        }
        List<ExpertGraphDimension> currentDimensions =
                dimensionViewService.dimensions(evidence.getUserId(), "DRAFT");
        Map<String, ExpertGraphDimension> dimensions = currentDimensions.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ExpertGraphDimension::getDimensionCode, item -> item,
                        (left, right) -> left));
        List<ExpertServiceEvidence> recentEvidence =
                openApiMapper.selectRecentServiceEvidenceByUserId(evidence.getUserId(), 10);
        ExpertProfileEvidenceAgentService.Analysis agentAnalysis = null;
        String fallbackReason = null;
        try
        {
            agentAnalysis = evidenceAgentService.analyze(
                    profile, evidence, currentDimensions, recentEvidence);
        }
        catch (Exception ex)
        {
            fallbackReason = ex.getMessage();
        }

        int direction = direction(content);
        double itemConfidence = direction == 0 ? 0.45 : 0.72;
        List<ExpertProfileUpdateSuggestionItem> items = new ArrayList<>();
        String summary;
        String generationMode;
        String promptVersion;
        String modelName;
        String agentOutputJson = null;
        if (agentAnalysis != null)
        {
            if (agentAnalysis.items().isEmpty())
            {
                return null;
            }
            for (ExpertProfileEvidenceAgentService.Item agentItem : agentAnalysis.items())
            {
                ExpertGraphDimension dimension = dimensions.get(agentItem.dimensionCode());
                if (dimension == null)
                {
                    continue;
                }
                ExpertProfileUpdateSuggestionItem item = new ExpertProfileUpdateSuggestionItem();
                item.setDimensionCode(agentItem.dimensionCode());
                item.setDimensionName(dimension.getDimensionName());
                item.setBeforeScore(score(dimension));
                item.setSuggestedScore(clamp(agentItem.suggestedScore()));
                item.setReason(truncate(agentItem.reason(), 900));
                item.setEvidence(truncate(agentItem.evidence(), 1900));
                item.setConfidence(agentItem.confidence());
                items.add(item);
            }
            if (items.isEmpty())
            {
                return null;
            }
            summary = agentAnalysis.summary();
            itemConfidence = agentAnalysis.confidence();
            generationMode = "AGENT";
            promptVersion = ExpertProfileEvidenceAgentService.PROMPT_VERSION;
            modelName = evidenceAgentService.getModelName();
            agentOutputJson = agentAnalysis.rawOutput();
        }
        else
        {
            List<String> targetCodes = targetDimensions(evidence.getEvidenceType());
            for (String code : targetCodes)
            {
                ExpertGraphDimension dimension = dimensions.get(code);
                if (dimension == null)
                {
                    continue;
                }
                double before = score(dimension);
                double suggested = clamp(before
                        + (direction > 0 ? 3 : direction < 0 ? -5 : 0));
                ExpertProfileUpdateSuggestionItem item = new ExpertProfileUpdateSuggestionItem();
                item.setDimensionCode(code);
                item.setDimensionName(dimension.getDimensionName());
                item.setBeforeScore(before);
                item.setSuggestedScore(suggested);
                item.setReason(reason(evidence.getEvidenceType(), direction));
                item.setEvidence(truncate(content, 1900));
                item.setConfidence(itemConfidence);
                items.add(item);
            }
            if (items.isEmpty())
            {
                return null;
            }
            summary = "Agent不可用，使用规则基线。"
                    + (StringUtils.isEmpty(fallbackReason) ? "" : "原因：" + fallbackReason)
                    + " " + summary(evidence, direction, items);
            generationMode = "RULE_FALLBACK";
            promptVersion = ExpertProfileEvidenceAgentService.PROMPT_VERSION;
            modelName = evidenceAgentService.getModelName();
        }
        if (items.isEmpty())
        {
            return null;
        }

        ExpertProfileUpdateSuggestion suggestion = new ExpertProfileUpdateSuggestion();
        suggestion.setEvidenceId(evidence.getEvidenceId());
        suggestion.setUserId(evidence.getUserId());
        suggestion.setStatus("PENDING");
        suggestion.setSummary(truncate(summary, 1900));
        suggestion.setConfidence(itemConfidence);
        suggestion.setGenerationMode(generationMode);
        suggestion.setPromptVersion(promptVersion);
        suggestion.setModelName(modelName);
        suggestion.setAgentOutputJson(agentOutputJson);
        updateMapper.insertSuggestion(suggestion);
        for (ExpertProfileUpdateSuggestionItem item : items)
        {
            item.setSuggestionId(suggestion.getSuggestionId());
            updateMapper.insertItem(item);
        }
        suggestion.setItems(items);
        return suggestion;
    }

    @Transactional(readOnly = true)
    public List<ExpertProfileUpdateSuggestion> list(String status)
    {
        List<ExpertProfileUpdateSuggestion> suggestions =
                updateMapper.selectList(status);
        suggestions.forEach(this::loadItems);
        return suggestions;
    }

    @Transactional(readOnly = true)
    public ExpertProfileUpdateSuggestion detail(Long suggestionId)
    {
        ExpertProfileUpdateSuggestion suggestion = updateMapper.selectById(suggestionId);
        if (suggestion == null)
        {
            throw new ServiceException("画像更新建议不存在");
        }
        loadItems(suggestion);
        return suggestion;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> approvedForExpert(Long userId)
    {
        if (userId == null)
        {
            return List.of();
        }
        List<ExpertProfileUpdateSuggestion> suggestions =
                updateMapper.selectApprovedByUserId(userId, 20);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExpertProfileUpdateSuggestion suggestion : suggestions)
        {
            loadItems(suggestion);
            Map<String, Object> review = new LinkedHashMap<>();
            review.put("suggestionId", suggestion.getSuggestionId());
            review.put("summary", effectiveSummary(suggestion));
            review.put("originalSummary", suggestion.getSummary());
            review.put("reviewedSummary", suggestion.getReviewedSummary());
            review.put("confidence", suggestion.getConfidence());
            review.put("reviewerName", suggestion.getReviewerName());
            review.put("reviewComment", suggestion.getReviewComment());
            review.put("releaseId", suggestion.getReleaseId());
            review.put("resolvedTime", suggestion.getResolvedTime());
            review.put("items", suggestion.getItems().stream().map(item -> {
                Map<String, Object> dimension = new LinkedHashMap<>();
                dimension.put("itemId", item.getItemId());
                dimension.put("dimensionCode", item.getDimensionCode());
                dimension.put("dimensionName", item.getDimensionName());
                dimension.put("beforeScore", item.getBeforeScore());
                dimension.put("suggestedScore", item.getSuggestedScore());
                dimension.put("approvedScore", item.getApprovedScore());
                dimension.put("reason", effectiveReason(item));
                dimension.put("originalReason", item.getReason());
                dimension.put("reviewedReason", item.getReviewedReason());
                return dimension;
            }).toList());
            result.add(review);
        }
        return result;
    }

    @Transactional
    public ExpertProfileUpdateSuggestion review(Long suggestionId,
            ExpertProfileUpdateReviewRequest request, LoginUser reviewer)
    {
        String action = String.valueOf(request.getAction()).toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(action) && !"REJECT".equals(action)
                && !"SAVE".equals(action))
        {
            throw new ServiceException("审核动作仅支持 SAVE、APPROVE 或 REJECT");
        }
        if ("REJECT".equals(action) && StringUtils.isEmpty(request.getComment()))
        {
            throw new ServiceException("驳回画像更新建议时必须填写审核意见");
        }
        ExpertProfileUpdateSuggestion suggestion =
                updateMapper.selectByIdForUpdate(suggestionId);
        if (suggestion == null || !"PENDING".equals(suggestion.getStatus()))
        {
            throw new ServiceException("画像更新建议已经处理或不存在");
        }
        if (!Objects.equals(suggestion.getVersion(), request.getVersion()))
        {
            throw new ServiceException("画像更新建议已被其他审核人处理，请刷新后重试");
        }
        loadItems(suggestion);
        Map<Long, ExpertProfileUpdateItemReview> reviewedItems =
                reviewedItems(request, suggestion);
        applyReviewedOpinions(request, suggestion, reviewedItems, action);
        if ("SAVE".equals(action))
        {
            if (updateMapper.touchPendingSuggestion(suggestionId,
                    request.getVersion()) == 0)
            {
                throw new ServiceException("画像更新建议已被其他审核人处理，请刷新后重试");
            }
            return detail(suggestionId);
        }
        Long releaseId = null;
        if ("APPROVE".equals(action))
        {
            for (ExpertProfileUpdateSuggestionItem item : suggestion.getItems())
            {
                ExpertProfileUpdateItemReview reviewed = reviewedItems.get(item.getItemId());
                double approved = reviewed == null || reviewed.getApprovedScore() == null
                        ? item.getSuggestedScore() : reviewed.getApprovedScore();
                updateMapper.updateItemApprovedScore(item.getItemId(), approved);
                item.setApprovedScore(approved);
            }
            releaseId = createProfileRelease(suggestion, reviewer.getUsername());
            openApiMapper.updateServiceEvidenceStatus(
                    suggestion.getEvidenceId(), "APPLIED");
        }
        else
        {
            openApiMapper.updateServiceEvidenceStatus(
                    suggestion.getEvidenceId(), "REJECTED");
        }
        if (updateMapper.resolveSuggestion(suggestionId, action.equals("APPROVE")
                ? "APPROVED" : "REJECTED", request.getVersion(), reviewer.getUserId(),
                reviewer.getUsername(), request.getComment(), releaseId) == 0)
        {
            throw new ServiceException("画像更新建议已被其他审核人处理，请刷新后重试");
        }
        return detail(suggestionId);
    }

    private Long createProfileRelease(ExpertProfileUpdateSuggestion suggestion,
            String approvedBy)
    {
        Long userId = suggestion.getUserId();
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在，不能生成画像版本");
        }
        Map<String, Object> latest =
                platformMapper.selectLatestExpertReleaseVersion(userId);
        JSONObject profileSnapshot = parseObject(
                latest == null ? null : latest.get("profileSnapshotJson"));
        JSONArray dimensions = profileSnapshot.getJSONArray("dimensions");
        if (dimensions == null || dimensions.isEmpty())
        {
            dimensions = JSONArray.from(dimensionViewService.dimensions(userId, "DRAFT"));
            profileSnapshot.put("dimensions", dimensions);
        }
        Map<String, Double> approvedByCode = new HashMap<>();
        for (ExpertProfileUpdateSuggestionItem item : suggestion.getItems())
        {
            double score = item.getApprovedScore() == null
                    ? item.getSuggestedScore() : item.getApprovedScore();
            approvedByCode.put(item.getDimensionCode(), clamp(score));
        }
        for (Object raw : dimensions)
        {
            if (!(raw instanceof Map<?, ?>))
            {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dimension = (Map<String, Object>) raw;
            Object code = dimension.get("dimensionCode");
            if (code != null && approvedByCode.containsKey(String.valueOf(code)))
            {
                ExpertProfileUpdateSuggestionItem item = suggestion.getItems().stream()
                        .filter(candidate -> candidate.getDimensionCode()
                                .equals(String.valueOf(code)))
                        .findFirst()
                        .orElse(null);
                dimension.put("score", approvedByCode.get(String.valueOf(code)));
                dimension.put("dataStatus", "AVAILABLE");
                dimension.put("evidence", item == null ? effectiveSummary(suggestion)
                        : effectiveReason(item));
            }
        }

        int graphVersion = integerValue(latest == null
                ? profile.getGraphVersion() : latest.get("graphVersion")) + 1;
        int versionNo = integerValue(latest == null ? 0 : latest.get("versionNo")) + 1;
        Object sourceMaterials = latest == null ? null
                : latest.get("sourceMaterialVersionsJson");
        if (sourceMaterials == null)
        {
            sourceMaterials = JSON.toJSONString(
                    platformMapper.selectEffectiveMaterialVersions(userId));
        }
        Object graphSnapshot = latest == null ? null : latest.get("graphSnapshotJson");
        if (graphSnapshot == null)
        {
            graphSnapshot = JSON.toJSONString(graphQueryService.expertSubgraph(userId, 3));
        }

        Map<String, Object> release = new LinkedHashMap<>();
        release.put("userId", userId);
        release.put("versionNo", versionNo);
        release.put("graphVersion", graphVersion);
        release.put("sourceMaterialVersionsJson", String.valueOf(sourceMaterials));
        release.put("profileSnapshotJson", JSON.toJSONString(profileSnapshot));
        release.put("graphSnapshotJson", String.valueOf(graphSnapshot));
        release.put("approvedBy", approvedBy);
        release.put("triggerType", "SERVICE_EVIDENCE_APPROVED");
        platformMapper.insertExpertReleaseVersion(release);
        Map<String, Object> saved = platformMapper.selectLatestExpertReleaseVersion(userId);
        updateMapper.updateProfileGraphVersion(userId, graphVersion);
        return saved == null ? null : longValue(saved.get("releaseId"));
    }

    private Map<Long, ExpertProfileUpdateItemReview> reviewedItems(
            ExpertProfileUpdateReviewRequest request,
            ExpertProfileUpdateSuggestion suggestion)
    {
        Map<Long, ExpertProfileUpdateItemReview> result = new HashMap<>();
        if (request.getItems() == null)
        {
            return result;
        }
        Map<Long, ExpertProfileUpdateSuggestionItem> validItems = new HashMap<>();
        suggestion.getItems().forEach(item -> validItems.put(item.getItemId(), item));
        for (ExpertProfileUpdateItemReview review : request.getItems())
        {
            if (!validItems.containsKey(review.getItemId()))
            {
                throw new ServiceException("画像更新明细不属于当前建议");
            }
            result.put(review.getItemId(), review);
        }
        return result;
    }

    private void applyReviewedOpinions(ExpertProfileUpdateReviewRequest request,
            ExpertProfileUpdateSuggestion suggestion,
            Map<Long, ExpertProfileUpdateItemReview> reviewedItems, String action)
    {
        if (request.getReviewedSummary() != null)
        {
            if (request.getReviewedSummary().isBlank())
            {
                throw new ServiceException("审核修订摘要不能为空");
            }
            updateMapper.updateReviewedSummary(suggestion.getSuggestionId(),
                    truncate(request.getReviewedSummary(), 1900));
            suggestion.setReviewedSummary(request.getReviewedSummary());
        }
        for (ExpertProfileUpdateSuggestionItem item : suggestion.getItems())
        {
            ExpertProfileUpdateItemReview reviewed = reviewedItems.get(item.getItemId());
            if (reviewed == null)
            {
                continue;
            }
            if (reviewed.getReviewedReason() != null)
            {
                if (reviewed.getReviewedReason().isBlank())
                {
                    throw new ServiceException("审核修订理由不能为空");
                }
                String reason = truncate(reviewed.getReviewedReason(), 900);
                updateMapper.updateItemReviewedReason(item.getItemId(), reason);
                item.setReviewedReason(reason);
            }
            if ("SAVE".equals(action) && reviewed.getApprovedScore() != null)
            {
                double score = clamp(reviewed.getApprovedScore());
                updateMapper.updateItemApprovedScore(item.getItemId(), score);
                item.setApprovedScore(score);
            }
        }
    }

    private static String effectiveSummary(ExpertProfileUpdateSuggestion suggestion)
    {
        return StringUtils.isEmpty(suggestion.getReviewedSummary())
                ? suggestion.getSummary() : suggestion.getReviewedSummary();
    }

    private static String effectiveReason(ExpertProfileUpdateSuggestionItem item)
    {
        return StringUtils.isEmpty(item.getReviewedReason())
                ? item.getReason() : item.getReviewedReason();
    }

    private void loadItems(ExpertProfileUpdateSuggestion suggestion)
    {
        suggestion.setItems(updateMapper.selectItems(suggestion.getSuggestionId()));
    }

    private static List<String> targetDimensions(String evidenceType)
    {
        return switch (String.valueOf(evidenceType).toUpperCase(Locale.ROOT))
        {
            case "SERVICE_REPORT" -> List.of("PERF_REPORT_QUALITY", "PERF_SIMILAR_PROJECT");
            case "EVALUATION" -> List.of("SERVICE_RESPONSE", "PERF_COMPLETION_CREDIT");
            case "CREDIT" -> List.of("PERF_COMPLETION_CREDIT", "SERVICE_COMPLIANCE");
            default -> List.of();
        };
    }

    private static int direction(String content)
    {
        int positive = POSITIVE_TERMS.stream().filter(content::contains).mapToInt(item -> 1).sum();
        int negative = NEGATIVE_TERMS.stream().filter(content::contains).mapToInt(item -> 1).sum();
        return Integer.compare(positive, negative);
    }

    private static String reason(String evidenceType, int direction)
    {
        String change = direction > 0 ? "建议小幅上调"
                : direction < 0 ? "建议下调并要求人工复核" : "建议记录事实并由人工判断是否调整";
        return evidenceType + " 证据：" + change + "相关画像维度。";
    }

    private static String summary(ExpertServiceEvidence evidence, int direction,
            List<ExpertProfileUpdateSuggestionItem> items)
    {
        String tone = direction > 0 ? "正向" : direction < 0 ? "负向" : "中性";
        return "来源 " + evidence.getSourceSystem() + " 的 " + evidence.getEvidenceType()
                + " 证据呈现" + tone + "信号，拟更新 "
                + String.join("、", items.stream()
                        .map(ExpertProfileUpdateSuggestionItem::getDimensionName).toList())
                + "；批准前不修改生效画像。";
    }

    private static String content(ExpertServiceEvidence evidence)
    {
        return String.join(" ", nullSafe(evidence.getTitle()),
                nullSafe(evidence.getSummary()), nullSafe(evidence.getRawPayload())).trim();
    }

    private static String nullSafe(String value)
    {
        return value == null ? "" : value;
    }

    private static double score(ExpertGraphDimension dimension)
    {
        return dimension.getScore() == null ? 0 : dimension.getScore().doubleValue();
    }

    private static double clamp(double value)
    {
        return Math.max(0, Math.min(100, Math.round(value * 100.0) / 100.0));
    }

    private static int integerValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        try
        {
            return Integer.parseInt(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return 0;
        }
    }

    private static Long longValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static String truncate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static JSONObject parseObject(Object value)
    {
        if (value == null)
        {
            return new JSONObject();
        }
        try
        {
            JSONObject result = JSON.parseObject(String.valueOf(value));
            return result == null ? new JSONObject() : result;
        }
        catch (Exception ex)
        {
            return new JSONObject();
        }
    }
}
