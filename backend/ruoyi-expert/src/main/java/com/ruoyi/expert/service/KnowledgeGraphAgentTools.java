package com.ruoyi.expert.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * GraphRAG Agent 的只读工具集合。
 */
@Service
public class KnowledgeGraphAgentTools
{
    private static final List<String> MATCH_ENTITY_TYPES = List.of(
            "Capability", "Industry", "ServiceRegion", "Project",
            "Achievement", "Technology", "Credential", "Organization");
    private static final Map<String, String> PROVINCE_BY_CITY = Map.ofEntries(
            Map.entry("南昌", "江西"), Map.entry("赣州", "江西"),
            Map.entry("九江", "江西"), Map.entry("上饶", "江西"),
            Map.entry("宜春", "江西"), Map.entry("吉安", "江西"),
            Map.entry("抚州", "江西"), Map.entry("景德镇", "江西"),
            Map.entry("萍乡", "江西"), Map.entry("新余", "江西"),
            Map.entry("鹰潭", "江西"), Map.entry("北京", "北京"),
            Map.entry("上海", "上海"), Map.entry("天津", "天津"),
            Map.entry("重庆", "重庆"), Map.entry("杭州", "浙江"),
            Map.entry("南京", "江苏"), Map.entry("广州", "广东"),
            Map.entry("深圳", "广东"), Map.entry("武汉", "湖北"),
            Map.entry("长沙", "湖南"), Map.entry("合肥", "安徽"));

    @Autowired
    private Neo4jHttpClient neo4j;

    @Autowired
    private KnowledgeGraphQueryService queryService;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    public JSONArray definitions()
    {
        JSONArray tools = new JSONArray();
        tools.add(tool("kg_search_experts",
                "第一阶段：以产业为中心，沿行业→专家→关键事实节点检索已认证专家，并返回图谱命中。",
                objectSchema(
                        Map.of(
                                "terms", arraySchema("string", "需求关键词，建议 1-8 个"),
                                "entityTypes", arraySchema("string", "可选实体类型过滤"),
                                "limit", numberSchema("最多返回人数，默认 8")),
                        List.of("terms"))));
        tools.add(tool("kg_find_paths",
                "查询指定专家与需求关键词之间的最多三跳图谱路径及关系ID。",
                objectSchema(
                        Map.of(
                                "expertExternalKey", stringSchema("例如 expert:12"),
                                "terms", arraySchema("string", "需求关键词"),
                                "limit", numberSchema("最多路径数，默认 20")),
                        List.of("expertExternalKey", "terms"))));
        tools.add(tool("kg_expand_evidence",
                "根据关系ID或实体ID读取 MySQL 中的材料证据。",
                objectSchema(
                        Map.of(
                                "relationIds", arraySchema("number", "关系ID"),
                                "entityIds", arraySchema("number", "实体ID")),
                        List.of())));
        tools.add(tool("expert_check_availability",
                "第二阶段：执行认证、接单、区域、方式和日期硬门禁，并按任务相关30维画像精排。",
                objectSchema(
                        Map.of(
                                "expertExternalKey", stringSchema("例如 expert:12"),
                                "serviceDate", stringSchema("服务日期 YYYY-MM-DD，可选"),
                                "serviceRegion", stringSchema("服务区域，可选"),
                                "serviceMode", stringSchema("REMOTE/ONSITE/BOTH，可选"),
                                "industry", stringSchema("目标产业，可选"),
                                "profileTerms", arraySchema("string",
                                        "用于匹配30维画像名称和说明的检索词，可选"),
                                "matchCount", numberSchema("图谱命中数，可选")),
                        List.of("expertExternalKey"))));
        tools.add(tool("expert_filter_available",
                "第二阶段：对图谱候选批量执行硬门禁，并使用相关30维画像进行精排。",
                objectSchema(
                        Map.of(
                                "expertExternalKeys", arraySchema("string",
                                        "候选专家 externalKey 列表，例如 expert:12"),
                                "matchCounts", arraySchema("number",
                                        "与专家列表顺序一致的图谱命中数"),
                                "serviceDate", stringSchema("服务日期 YYYY-MM-DD，可选"),
                                "serviceRegion", stringSchema("服务区域，可选"),
                                "serviceMode", stringSchema("REMOTE/ONSITE/BOTH，可选"),
                                "industry", stringSchema("目标产业，可选"),
                                "profileTerms", arraySchema("string",
                                        "用于匹配30维画像名称和说明的检索词，可选")),
                        List.of("expertExternalKeys"))));
        return tools;
    }

    public JSONObject execute(String toolName, JSONObject arguments)
    {
        return switch (toolName)
        {
            case "kg_search_experts", "kg.search_experts" -> searchExperts(arguments);
            case "kg_find_paths", "kg.find_paths" -> findPaths(arguments);
            case "kg_expand_evidence", "kg.expand_evidence" -> expandEvidence(arguments);
            case "expert_check_availability", "expert.check_availability" -> checkAvailability(arguments);
            case "expert_filter_available", "expert.filter_available" -> filterAvailable(arguments);
            default -> throw new ServiceException("未知 Agent 工具：" + toolName);
        };
    }

    public List<String> suggestTerms(String objective)
    {
        if (!neo4j.isEnabled() || StringUtils.isEmpty(objective))
        {
            return fallbackTerms(objective);
        }
        List<Map<String, Object>> rows = neo4j.query("""
                MATCH (n:KGNode)
                WHERE n.entityType IN $entityTypes
                  AND size(n.canonicalName) >= 2
                  AND toLower($objective) CONTAINS toLower(n.canonicalName)
                RETURN DISTINCT n.canonicalName AS name
                ORDER BY size(n.canonicalName) DESC
                LIMIT 20
                """, Map.of("entityTypes", MATCH_ENTITY_TYPES, "objective", objective));
        List<String> result = rows.stream()
                .map(row -> String.valueOf(row.get("name")))
                .filter(StringUtils::isNotEmpty)
                .toList();
        return result.isEmpty() ? fallbackTerms(objective) : result;
    }

    private JSONObject searchExperts(JSONObject arguments)
    {
        List<String> terms = stringList(arguments.get("terms"));
        List<String> entityTypes = stringList(arguments.get("entityTypes"));
        if (terms.isEmpty())
        {
            throw new ServiceException("search_experts 至少需要一个需求关键词");
        }
        if (entityTypes.isEmpty())
        {
            entityTypes = MATCH_ENTITY_TYPES;
        }
        int limit = boundedInt(arguments.get("limit"), 8, 1, 20);
        List<Map<String, Object>> rows = neo4j.query("""
                MATCH (industry:KGNode {entityType: 'Industry'})
                      -[ir:KG_RELATION]->
                      (expert:KGNode {entityType: 'Expert'})
                WHERE industry.reviewStatus = 'ACCEPTED'
                  AND expert.reviewStatus = 'ACCEPTED'
                  AND ir.relationType = 'HAS_EXPERT'
                  AND coalesce(ir.active, true)
                  AND ir.reviewStatus = 'ACCEPTED'
                  AND ir.validTo IS NULL
                  AND ir.ownerExpertId =
                      toInteger(split(expert.externalKey, ':')[1])
                OPTIONAL MATCH (expert)-[rels:KG_RELATION*1..2]->(node:KGNode)
                WHERE all(r IN rels
                          WHERE coalesce(r.active, true)
                            AND r.reviewStatus = 'ACCEPTED'
                            AND r.validTo IS NULL
                            AND r.ownerExpertId =
                                toInteger(split(expert.externalKey, ':')[1]))
                  AND node.entityType IN $entityTypes
                  AND node.reviewStatus = 'ACCEPTED'
                WITH expert,
                     collect(DISTINCT CASE
                       WHEN any(term IN $terms WHERE
                           toLower(industry.canonicalName) CONTAINS toLower(term)
                           OR toLower(coalesce(industry.propertiesJson, '')
                               ) CONTAINS toLower(term))
                       THEN {
                         term: head([term IN $terms WHERE
                           toLower(industry.canonicalName) CONTAINS toLower(term)
                           OR toLower(coalesce(industry.propertiesJson, '')
                               ) CONTAINS toLower(term)]),
                         entityId: industry.entityId,
                         name: industry.canonicalName,
                         entityType: industry.entityType
                       }
                       ELSE null END) AS rawIndustries,
                     collect(DISTINCT CASE
                       WHEN node IS NOT NULL AND any(term IN $terms WHERE
                           toLower(node.canonicalName) CONTAINS toLower(term)
                           OR toLower(coalesce(node.propertiesJson, '')
                               ) CONTAINS toLower(term))
                       THEN {
                         term: head([term IN $terms WHERE
                           toLower(node.canonicalName) CONTAINS toLower(term)
                           OR toLower(coalesce(node.propertiesJson, '')
                               ) CONTAINS toLower(term)]),
                         entityId: node.entityId,
                         name: node.canonicalName,
                         entityType: node.entityType
                       }
                       ELSE null END) AS rawNodes
                WITH expert,
                     [item IN rawIndustries WHERE item IS NOT NULL] AS industries,
                     [item IN rawNodes WHERE item IS NOT NULL] AS nodes
                WITH expert, industries, nodes, industries + nodes AS matched
                WHERE size(matched) > 0
                RETURN expert.entityId AS expertEntityId,
                       expert.externalKey AS externalKey,
                       expert.canonicalName AS expertName,
                       expert.reviewStatus AS reviewStatus,
                       size(matched) AS matchCount,
                       matched
                ORDER BY matchCount DESC, expertName
                LIMIT $limit
                """, Map.of("terms", terms, "entityTypes", entityTypes, "limit", limit));
        JSONObject result = new JSONObject();
        result.put("tool", "kg_search_experts");
        result.put("terms", terms);
        result.put("candidates", rows);
        result.put("candidateCount", rows.size());
        return result;
    }

    private JSONObject findPaths(JSONObject arguments)
    {
        String expertKey = arguments.getString("expertExternalKey");
        List<String> terms = stringList(arguments.get("terms"));
        if (StringUtils.isEmpty(expertKey) || terms.isEmpty())
        {
            throw new ServiceException("find_paths 需要 expertExternalKey 和 terms");
        }
        int limit = boundedInt(arguments.get("limit"), 20, 1, 50);
        List<Map<String, Object>> rows = neo4j.query("""
                UNWIND $terms AS term
                MATCH p=(industry:KGNode {entityType: 'Industry'})
                        -[:KG_RELATION]->
                        (expert:KGNode {entityType: 'Expert', externalKey: $expertKey})
                        -[rels:KG_RELATION*1..2]->(node:KGNode)
                WHERE industry.reviewStatus = 'ACCEPTED'
                  AND expert.reviewStatus = 'ACCEPTED'
                  AND all(r IN relationships(p)
                          WHERE coalesce(r.active, true)
                            AND r.reviewStatus = 'ACCEPTED'
                            AND r.validTo IS NULL
                            AND r.ownerExpertId =
                                toInteger(split(expert.externalKey, ':')[1]))
                  AND node.entityType IN $entityTypes
                  AND node.reviewStatus = 'ACCEPTED'
                  AND (toLower(node.canonicalName) CONTAINS toLower(term)
                       OR toLower(coalesce(node.propertiesJson, '')) CONTAINS toLower(term))
                RETURN node.entityId AS targetEntityId,
                       node.canonicalName AS targetName,
                       node.entityType AS targetType,
                       term,
                       [node IN nodes(p) | {
                           entityId: node.entityId,
                           entityType: node.entityType,
                           canonicalName: node.canonicalName
                       }] AS nodes,
                       [rel IN relationships(p) | {
                           relationId: rel.relationId,
                           relationType: rel.relationType,
                           reviewStatus: rel.reviewStatus,
                           evidenceCount: rel.evidenceCount
                       }] AS relations
                LIMIT $limit
                """, Map.of("expertKey", expertKey, "terms", terms,
                        "entityTypes", MATCH_ENTITY_TYPES, "limit", limit));
        JSONObject result = new JSONObject();
        result.put("tool", "kg_find_paths");
        result.put("expertExternalKey", expertKey);
        result.put("paths", rows);
        result.put("pathCount", rows.size());
        return result;
    }

    private JSONObject expandEvidence(JSONObject arguments)
    {
        List<Long> relationIds = longList(arguments.get("relationIds"));
        List<Long> entityIds = longList(arguments.get("entityIds"));
        JSONObject result = new JSONObject();
        result.put("tool", "kg_expand_evidence");
        result.put("relationEvidence", queryService.evidenceForRelations(relationIds).stream()
                .limit(12).map(KnowledgeGraphAgentTools::compactEvidence).toList());
        result.put("entityEvidence", queryService.evidenceForEntities(entityIds).stream()
                .limit(12).map(KnowledgeGraphAgentTools::compactEvidence).toList());
        return result;
    }

    private JSONObject checkAvailability(JSONObject arguments)
    {
        Long userId = parseUserKey(arguments.getString("expertExternalKey"));
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        List<ExpertSchedule> schedules = profile == null ? List.of()
                : platformMapper.selectScheduleByUserId(userId);
        List<ExpertUnavailablePeriod> unavailablePeriods = profile == null ? List.of()
                : platformMapper.selectUnavailablePeriodsByUserId(userId);
        List<ExpertGraphDimension> dimensions = profile == null ? List.of()
                : dimensionViewService.dimensions(userId, "DRAFT");
        JSONArray checks = new JSONArray();
        JSONObject result = new JSONObject();
        result.put("tool", "expert_check_availability");
        result.put("expertExternalKey", "expert:" + userId);
        result.put("profileExists", profile != null);
        if (profile != null)
        {
            result.put("reviewStatus", profile.getReviewStatus());
            result.put("serviceStatus", profile.getServiceStatus());
            result.put("schedules", schedules);
            result.put("unavailablePeriods", unavailablePeriods);
        }
        boolean eligible = profile != null;
        eligible &= gate(checks, "PROFILE", profile != null,
                profile != null ? "专家档案存在" : "专家档案不存在");
        eligible &= gate(checks, "CERTIFICATION",
                profile != null && "APPROVED".equals(profile.getReviewStatus()),
                "正式认证已通过");
        eligible &= gate(checks, "SERVICE_STATUS",
                profile != null && "AVAILABLE".equals(profile.getServiceStatus()),
                "当前允许接单");
        eligible &= gate(checks, "SCHEDULE",
                schedules.stream().anyMatch(item ->
                        integer(item.getWeeklyCapacity()) > 0),
                "存在可服务时间表");

        String serviceMode = upper(arguments.getString("serviceMode"));
        if (StringUtils.isNotEmpty(serviceMode))
        {
            eligible &= gate(checks, "SERVICE_MODE",
                    schedules.stream().anyMatch(item -> modeMatches(item, serviceMode)),
                    "服务方式覆盖 " + serviceMode);
        }

        String serviceRegion = arguments.getString("serviceRegion");
        if (StringUtils.isNotEmpty(serviceRegion))
        {
            eligible &= gate(checks, "REGION",
                    profile != null && regionMatches(profile.getServiceRegion(), serviceRegion),
                    "服务区域覆盖 " + serviceRegion);
        }

        String industry = arguments.getString("industry");
        if (StringUtils.isNotEmpty(industry))
        {
            eligible &= gate(checks, "INDUSTRY",
                    profile != null && regionMatches(profile.getIndustryField(), industry),
                    "产业方向覆盖 " + industry);
        }

        String serviceDate = arguments.getString("serviceDate");
        if (StringUtils.isNotEmpty(serviceDate))
        {
            LocalDate date = parseDate(serviceDate);
            boolean dateCovered = date != null && schedules.stream()
                    .anyMatch(item -> scheduleCovers(item, date, serviceMode));
            eligible &= gate(checks, "SERVICE_DATE", dateCovered,
                    dateCovered ? serviceDate + " 有可服务时段" : serviceDate + " 无可服务时段");
            result.put("serviceDate", serviceDate);
            result.put("matchedSchedules", schedules.stream()
                    .filter(item -> date != null && scheduleCovers(item, date, serviceMode))
                    .toList());
            List<ExpertUnavailablePeriod> unavailableOnDate = unavailablePeriods.stream()
                    .filter(item -> date != null && unavailableOnDate(item, date))
                    .toList();
            boolean temporarilyUnavailable = !unavailableOnDate.isEmpty();
            eligible &= gate(checks, "TEMPORARY_UNAVAILABILITY",
                    !temporarilyUnavailable,
                    temporarilyUnavailable
                            ? serviceDate + " 处于专家临时不可用时段"
                            : serviceDate + " 无临时不可用冲突");
            result.put("temporarilyUnavailable", temporarilyUnavailable);
            result.put("unavailablePeriodsOnDate", unavailableOnDate);
        }

        List<String> profileTerms = stringList(arguments.get("profileTerms"));
        JSONArray matchedDimensions = matchingDimensions(dimensions, profileTerms);
        double profileScore = matchedDimensions.isEmpty()
                ? averageProfileScore(dimensions) : averageProfileScore(matchedDimensions);
        double profileConfidence = averageConfidence(dimensions);
        double coverage = coverage(dimensions);
        int matchCount = Math.max(0, arguments.getIntValue("matchCount"));
        double semanticFit = Math.min(5, matchCount) * 20.0;
        double rankingScore = profileScore * 0.65
                + semanticFit * 0.25 + profileConfidence * 0.1;
        result.put("hardGateEligible", eligible);
        result.put("gateChecks", checks);
        result.put("profileScore", round(profileScore));
        result.put("profileScoreScope",
                matchedDimensions.isEmpty() ? "ALL_DIMENSIONS" : "MATCHED_DIMENSIONS");
        result.put("profileDimensionMatches", matchedDimensions);
        result.put("profileConfidence", round(profileConfidence));
        result.put("profileCoverage", round(coverage));
        result.put("graphMatchCount", matchCount);
        result.put("rankingScore", round(rankingScore));
        return result;
    }

    private JSONObject filterAvailable(JSONObject arguments)
    {
        List<String> keys = stringList(arguments.get("expertExternalKeys"))
                .stream().limit(20).toList();
        List<Object> matchCounts = arguments.getJSONArray("matchCounts") == null
                ? List.of() : new ArrayList<>(arguments.getJSONArray("matchCounts"));
        JSONArray candidates = new JSONArray();
        JSONArray rejected = new JSONArray();
        for (int index = 0; index < keys.size(); index++)
        {
            JSONObject itemArguments = new JSONObject();
            itemArguments.put("expertExternalKey", keys.get(index));
            itemArguments.put("serviceDate", arguments.getString("serviceDate"));
            itemArguments.put("serviceRegion", arguments.getString("serviceRegion"));
            itemArguments.put("serviceMode", arguments.getString("serviceMode"));
            itemArguments.put("industry", arguments.getString("industry"));
            itemArguments.put("profileTerms", arguments.get("profileTerms"));
            Object matchCount = index < matchCounts.size() ? matchCounts.get(index) : null;
            itemArguments.put("matchCount", matchCount instanceof Number number
                    ? number.intValue() : 0);
            JSONObject item = checkAvailability(itemArguments);
            if (item.getBooleanValue("hardGateEligible"))
            {
                candidates.add(item);
            }
            else
            {
                rejected.add(item);
            }
        }
        JSONObject result = new JSONObject();
        result.put("tool", "expert_filter_available");
        result.put("candidateCount", keys.size());
        result.put("eligibleCount", candidates.size());
        result.put("candidates", candidates);
        result.put("rejected", rejected);
        return result;
    }

    private static boolean gate(JSONArray checks, String code, boolean passed, String message)
    {
        JSONObject check = new JSONObject();
        check.put("code", code);
        check.put("passed", passed);
        check.put("message", message);
        checks.add(check);
        return passed;
    }

    private static boolean modeMatches(ExpertSchedule schedule, String requestedMode)
    {
        String mode = upper(schedule.getServiceMode());
        return "BOTH".equals(mode) || "BOTH".equals(requestedMode) || mode.equals(requestedMode);
    }

    private static boolean scheduleCovers(ExpertSchedule schedule, LocalDate date,
            String requestedMode)
    {
        DayOfWeek weekday = date.getDayOfWeek();
        LocalDate effectiveFrom = parseDate(schedule.getEffectiveFrom());
        boolean effective = effectiveFrom == null || !date.isBefore(effectiveFrom);
        boolean mode = StringUtils.isEmpty(requestedMode)
                || modeMatches(schedule, requestedMode);
        return effective
                && weekday.getValue() == integer(schedule.getWeekday())
                && integer(schedule.getWeeklyCapacity()) > 0
                && mode;
    }

    private static LocalDate parseDate(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        }
        catch (DateTimeParseException ex)
        {
            return null;
        }
    }

    private static boolean unavailableOnDate(ExpertUnavailablePeriod period, LocalDate date)
    {
        LocalDateTime start = parseDateTime(period.getStartTime());
        LocalDateTime end = parseDateTime(period.getEndTime());
        if (start == null || end == null)
        {
            return false;
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        return start.isBefore(dayEnd) && end.isAfter(dayStart);
    }

    private static LocalDateTime parseDateTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"))
        {
            try
            {
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种允许的输入格式。
            }
        }
        return null;
    }

    private static boolean regionMatches(String expertValue, String requestedValue)
    {
        String expert = normalizeText(expertValue);
        String requested = normalizeText(requestedValue);
        return !expert.isEmpty() && !requested.isEmpty()
                && (expert.contains(requested) || requested.contains(expert)
                || province(expert).equals(province(requested)));
    }

    private static double averageProfileScore(List<ExpertGraphDimension> dimensions)
    {
        return dimensions.stream()
                .filter(item -> "AVAILABLE".equals(item.getDataStatus())
                        || "CONFLICT".equals(item.getDataStatus()))
                .mapToDouble(item -> number(item.getScore()))
                .average().orElse(0);
    }

    private static JSONArray matchingDimensions(List<ExpertGraphDimension> dimensions,
            List<String> profileTerms)
    {
        JSONArray matched = new JSONArray();
        if (profileTerms == null || profileTerms.isEmpty())
        {
            return matched;
        }
        for (ExpertGraphDimension dimension : dimensions)
        {
            if (!"AVAILABLE".equals(dimension.getDataStatus())
                    && !"CONFLICT".equals(dimension.getDataStatus()))
            {
                continue;
            }
            String searchable = ((dimension.getDimensionName() == null
                    ? "" : dimension.getDimensionName()) + " "
                    + (dimension.getDescription() == null
                    ? "" : dimension.getDescription())).toLowerCase(Locale.ROOT);
            List<String> hits = profileTerms.stream()
                    .map(String::trim)
                    .filter(term -> !term.isEmpty()
                            && searchable.contains(term.toLowerCase(Locale.ROOT)))
                    .distinct()
                    .toList();
            if (hits.isEmpty())
            {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("dimensionCode", dimension.getDimensionCode());
            item.put("dimensionName", dimension.getDimensionName());
            item.put("score", dimension.getScore());
            item.put("confidence", dimension.getConfidence());
            item.put("matchedTerms", hits);
            matched.add(item);
        }
        return matched;
    }

    private static double averageProfileScore(JSONArray dimensions)
    {
        if (dimensions == null || dimensions.isEmpty())
        {
            return 0;
        }
        double total = 0;
        for (int index = 0; index < dimensions.size(); index++)
        {
            total += number(dimensions.getJSONObject(index).get("score"));
        }
        return total / dimensions.size();
    }

    private static double averageConfidence(List<ExpertGraphDimension> dimensions)
    {
        return dimensions.stream()
                .filter(item -> "AVAILABLE".equals(item.getDataStatus())
                        || "CONFLICT".equals(item.getDataStatus()))
                .mapToDouble(item -> number(item.getConfidence()) * 100)
                .average().orElse(0);
    }

    private static double coverage(List<ExpertGraphDimension> dimensions)
    {
        if (dimensions.isEmpty())
        {
            return 0;
        }
        long available = dimensions.stream()
                .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus()))
                .count();
        return available * 100.0 / dimensions.size();
    }

    private static double number(Object value)
    {
        return value instanceof Number number ? number.doubleValue()
                : value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private static int integer(Object value)
    {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static double round(double value)
    {
        return Math.round(value * 10.0) / 10.0;
    }

    private static String upper(String value)
    {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static String normalizeText(String value)
    {
        return value == null ? "" : value.replaceAll("[\\s，,。.;；、]", "")
                .replaceAll("(壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市)$", "");
    }

    private static String province(String value)
    {
        return PROVINCE_BY_CITY.getOrDefault(value, value);
    }

    private static JSONObject tool(String name, String description, JSONObject parameters)
    {
        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private static JSONObject objectSchema(Map<String, Object> properties,
            List<String> required)
    {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static JSONObject stringSchema(String description)
    {
        JSONObject schema = new JSONObject();
        schema.put("type", "string");
        schema.put("description", description);
        return schema;
    }

    private static JSONObject numberSchema(String description)
    {
        JSONObject schema = new JSONObject();
        schema.put("type", "integer");
        schema.put("description", description);
        return schema;
    }

    private static JSONObject arraySchema(String itemType, String description)
    {
        JSONObject items = new JSONObject();
        items.put("type", itemType);
        JSONObject schema = new JSONObject();
        schema.put("type", "array");
        schema.put("description", description);
        schema.put("items", items);
        return schema;
    }

    private static int boundedInt(Object value, int fallback, int min, int max)
    {
        int number = value instanceof Number
                ? ((Number) value).intValue() : fallback;
        return Math.max(min, Math.min(max, number));
    }

    private static Map<String, Object> compactEvidence(Map<String, Object> source)
    {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceId", source.get("evidenceId"));
        evidence.put("relationId", source.get("relationId"));
        evidence.put("entityId", source.get("entityId"));
        evidence.put("sourceType", source.get("sourceType"));
        evidence.put("sourceRef", source.get("sourceRef"));
        evidence.put("materialId", source.get("materialId"));
        evidence.put("pageNo", source.get("pageNo"));
        evidence.put("charStart", source.get("charStart"));
        evidence.put("charEnd", source.get("charEnd"));
        evidence.put("confidence", source.get("confidence"));
        Object excerpt = source.get("excerpt");
        if (excerpt != null && excerpt.toString().length() > 260)
        {
            evidence.put("excerpt", excerpt.toString().substring(0, 260));
        }
        else
        {
            evidence.put("excerpt", excerpt);
        }
        return evidence;
    }

    private static List<String> stringList(Object value)
    {
        Iterable<?> values;
        if (value instanceof Iterable<?> iterable)
        {
            values = iterable;
        }
        else if (value instanceof String text && !text.isBlank())
        {
            values = List.of(text);
        }
        else
        {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : values)
        {
            String text = String.valueOf(item).trim();
            if (!text.isEmpty() && !result.contains(text))
            {
                result.add(text);
            }
            if (result.size() >= 20)
            {
                break;
            }
        }
        return result;
    }

    private static List<Long> longList(Object value)
    {
        Iterable<?> values;
        if (value instanceof Iterable<?> iterable)
        {
            values = iterable;
        }
        else
        {
            return List.of();
        }
        List<Long> result = new ArrayList<>();
        for (Object item : values)
        {
            try
            {
                result.add(Long.valueOf(String.valueOf(item)));
            }
            catch (Exception ignored)
            {
                // Ignore malformed model arguments.
            }
            if (result.size() >= 50)
            {
                break;
            }
        }
        return result;
    }

    private static Long parseUserKey(String externalKey)
    {
        if (externalKey == null || !externalKey.startsWith("expert:"))
        {
            throw new ServiceException("专家键必须为 expert:<userId>");
        }
        return Long.valueOf(externalKey.substring("expert:".length()));
    }

    private static List<String> fallbackTerms(String objective)
    {
        if (StringUtils.isEmpty(objective))
        {
            return List.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String part : objective.split("[\\s，。；：、,.;!?]+"))
        {
            String term = part.trim();
            if (term.length() >= 2 && term.length() <= 30
                    && !List.of("需要", "专家", "寻找", "推荐", "能够", "具备").contains(term))
            {
                terms.add(term);
            }
        }
        return terms.stream().limit(10).toList();
    }
}
