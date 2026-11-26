package com.ruoyi.expert.service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/**
 * 模型分析结果的严格结构化校验。
 *
 * <p>模型输出只有在字段、维度、数值范围和证据都符合约定时才允许进入后续流程。
 * 缺失维度允许出现，用于表达证据不足；未知字段、未知维度和无证据结论则直接拒绝。</p>
 */
public final class ExpertAgentAnalysisValidator
{
    private static final Set<String> ROOT_FIELDS = Set.of("fields", "issues", "scores", "graphFacts");
    private static final Set<String> REQUIRED_ROOT_FIELDS = Set.of("fields", "issues", "scores");
    private static final Set<String> FIELD_PROPERTIES =
            Set.of("dbField", "fileField", "value", "confidence", "evidence", "conflict");
    private static final Set<String> SCORE_PROPERTIES =
            Set.of("score", "confidence", "evidence");
    private static final Set<String> ALLOWED_DB_FIELDS = Set.of(
            "real_name", "organization", "professional_title", "research_field",
            "industry_field", "service_region", "phone", "email");
    private static final Set<String> EMPTY_EVIDENCE_MARKERS = Set.of(
            "无", "无证据", "暂无", "暂无证据", "未知", "无法确认", "n/a", "none", "null");
    private static final Set<String> WEAK_EVIDENCE_MARKERS = Set.of(
            "未披露", "未提及", "未明确", "未提供", "无法准确", "无法判断",
            "可能具备", "可能涉及", "可能具有", "通常具备", "一般具备",
            "可合理推断", "应较高", "一定基础", "推测", "估计具备");
    private static final Set<String> GRAPH_ENTITY_TYPES = Set.of(
            "Industry", "Expert", "Organization", "Credential", "Capability",
            "Project", "Achievement", "Technology", "ServiceRegion");
    private static final Set<String> GRAPH_RELATION_TYPES = Set.of(
            "HAS_EXPERT", "BELONGS_TO", "HAS_CREDENTIAL", "EXPERTISE_IN", "WORKS_IN_INDUSTRY",
            "SERVES_REGION", "PARTICIPATED_IN", "PRODUCED", "PROJECT_PRODUCED",
            "USES_TECHNOLOGY", "APPLIES_TO", "TARGETS_INDUSTRY");

    private ExpertAgentAnalysisValidator()
    {
    }

    public static String canonicalEntityType(String value)
    {
        if (value == null)
        {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT))
        {
            case "region", "province", "city", "location", "serviceregion" -> "ServiceRegion";
            case "university", "unit", "company", "institution", "employer",
                    "organization" -> "Organization";
            case "degree", "title", "credential" -> "Credential";
            case "skill", "researchcapability", "capability" -> "Capability";
            case "paper", "patent", "publication", "achievement" -> "Achievement";
            case "method", "technology" -> "Technology";
            case "domain", "sector", "industry" -> "Industry";
            case "project" -> "Project";
            case "material" -> "Material";
            case "certification" -> "Certification";
            case "expert" -> "Expert";
            default -> value.trim();
        };
    }

    public static String canonicalRelationType(String value)
    {
        if (value == null)
        {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT))
        {
            case "WORKS_IN", "INDUSTRY" -> "WORKS_IN_INDUSTRY";
            case "SERVES", "LOCATED_IN" -> "SERVES_REGION";
            case "EXPERT_IN", "SPECIALIZES_IN" -> "EXPERTISE_IN";
            case "WORKED_AT", "EMPLOYED_BY" -> "BELONGS_TO";
            case "PUBLISHED" -> "PRODUCED";
            case "PROJECT_USES" -> "USES_TECHNOLOGY";
            case "TECH_APPLIES" -> "APPLIES_TO";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    public static boolean isKnownEntityType(String value)
    {
        return GRAPH_ENTITY_TYPES.contains(canonicalEntityType(value));
    }

    public static boolean isKnownRelationType(String value)
    {
        return GRAPH_RELATION_TYPES.contains(canonicalRelationType(value));
    }

    public static boolean hasUsableEvidence(String value)
    {
        return value != null && !value.isBlank()
                && !EMPTY_EVIDENCE_MARKERS.contains(value.trim().toLowerCase(Locale.ROOT))
                && !containsWeakMarker(value);
    }

    public static void validate(JSONObject analysis, Set<String> allowedDimensions)
    {
        require(analysis != null, "响应必须为 JSON 对象");
        requirePropertiesSubset(analysis, ROOT_FIELDS, "根对象");
        for (String required : REQUIRED_ROOT_FIELDS)
        {
            require(analysis.containsKey(required), "根对象缺少必要属性：" + required);
        }

        JSONArray fields = requireArray(analysis.get("fields"), "fields");
        JSONArray issues = requireArray(analysis.get("issues"), "issues");
        Object scoreValue = analysis.get("scores");
        require(scoreValue instanceof JSONObject, "scores 必须为 JSON 对象");

        for (int index = 0; index < fields.size(); index++)
        {
            Object value = fields.get(index);
            require(value instanceof JSONObject, "fields[" + index + "] 必须为 JSON 对象");
            JSONObject field = (JSONObject) value;
            requirePropertiesSubset(field, FIELD_PROPERTIES, "fields[" + index + "]");
            String dbField = requireText(field.get("dbField"), "fields[" + index + "].dbField");
            require(ALLOWED_DB_FIELDS.contains(dbField),
                    "fields[" + index + "].dbField 不在白名单中：" + dbField);
            requireText(field.get("fileField"), "fields[" + index + "].fileField");
            requireText(field.get("value"), "fields[" + index + "].value");
            requireRange(field.get("confidence"), 0.0, 1.0,
                    "fields[" + index + "].confidence");
            requireEvidence(field.get("evidence"), "fields[" + index + "].evidence");
            if (field.containsKey("conflict"))
            {
                require(field.get("conflict") instanceof Boolean,
                        "fields[" + index + "].conflict 必须为布尔值");
            }
        }

        for (int index = 0; index < issues.size(); index++)
        {
            requireText(issues.get(index), "issues[" + index + "]");
        }

        JSONObject scores = (JSONObject) scoreValue;
        for (Map.Entry<String, Object> entry : scores.entrySet())
        {
            String dimensionCode = entry.getKey();
            require(allowedDimensions.contains(dimensionCode),
                    "scores 包含未知维度：" + dimensionCode);
            require(entry.getValue() instanceof JSONObject,
                    "scores." + dimensionCode + " 必须为 JSON 对象");
            JSONObject score = (JSONObject) entry.getValue();
            requireExactProperties(score, SCORE_PROPERTIES, "scores." + dimensionCode);
            requireRange(score.get("score"), 0.0, 100.0,
                    "scores." + dimensionCode + ".score");
            requireRange(score.get("confidence"), 0.0, 1.0,
                    "scores." + dimensionCode + ".confidence");
            requireEvidence(score.get("evidence"), "scores." + dimensionCode + ".evidence");
        }

        if (analysis.containsKey("graphFacts"))
        {
            validateGraphFacts(analysis.get("graphFacts"));
        }
    }

    private static void validateGraphFacts(Object value)
    {
        require(value instanceof JSONObject, "graphFacts 必须为 JSON 对象");
        JSONObject facts = (JSONObject) value;
        requireExactProperties(facts, Set.of("entities", "relations"), "graphFacts");
        JSONArray entities = requireArray(facts.get("entities"), "graphFacts.entities");
        JSONArray relations = requireArray(facts.get("relations"), "graphFacts.relations");
        require(entities.size() <= 12, "graphFacts.entities 不能超过 12 条");
        require(relations.size() <= 12, "graphFacts.relations 不能超过 12 条");

        for (int index = 0; index < entities.size(); index++)
        {
            String path = "graphFacts.entities[" + index + "]";
            Object item = entities.get(index);
            require(item instanceof JSONObject, path + " 必须为 JSON 对象");
            JSONObject entity = (JSONObject) item;
            requireExactProperties(entity, Set.of(
                    "entityType", "name", "evidence", "confidence"), path);
            String entityType = requireText(entity.get("entityType"), path + ".entityType");
            require(GRAPH_ENTITY_TYPES.contains(entityType),
                    path + ".entityType 不在允许范围：" + entityType);
            requireText(entity.get("name"), path + ".name");
            requireEvidence(entity.get("evidence"), path + ".evidence");
            requireRange(entity.get("confidence"), 0.0, 1.0, path + ".confidence");
        }

        for (int index = 0; index < relations.size(); index++)
        {
            String path = "graphFacts.relations[" + index + "]";
            Object item = relations.get(index);
            require(item instanceof JSONObject, path + " 必须为 JSON 对象");
            JSONObject relation = (JSONObject) item;
            requireExactProperties(relation, Set.of(
                    "sourceType", "sourceName", "relationType",
                    "targetType", "targetName", "evidence", "confidence"), path);
            String sourceType = requireText(relation.get("sourceType"), path + ".sourceType");
            String targetType = requireText(relation.get("targetType"), path + ".targetType");
            String relationType = requireText(relation.get("relationType"), path + ".relationType");
            require(GRAPH_ENTITY_TYPES.contains(sourceType),
                    path + ".sourceType 不在允许范围：" + sourceType);
            require(GRAPH_ENTITY_TYPES.contains(targetType),
                    path + ".targetType 不在允许范围：" + targetType);
            require(GRAPH_RELATION_TYPES.contains(relationType),
                    path + ".relationType 不在允许范围：" + relationType);
            requireText(relation.get("sourceName"), path + ".sourceName");
            requireText(relation.get("targetName"), path + ".targetName");
            requireEvidence(relation.get("evidence"), path + ".evidence");
            requireRange(relation.get("confidence"), 0.0, 1.0, path + ".confidence");
        }
    }

    private static JSONArray requireArray(Object value, String path)
    {
        require(value instanceof JSONArray, path + " 必须为数组");
        return (JSONArray) value;
    }

    private static String requireText(Object value, String path)
    {
        require(value instanceof String, path + " 必须为字符串");
        String text = ((String) value).trim();
        require(!text.isEmpty(), path + " 不能为空");
        return text;
    }

    private static void requireEvidence(Object value, String path)
    {
        String evidence = requireText(value, path);
        require(!EMPTY_EVIDENCE_MARKERS.contains(evidence.toLowerCase(Locale.ROOT))
                        && !containsWeakMarker(evidence),
                path + " 不能使用无证据、缺失或推测性内容");
    }

    private static boolean containsWeakMarker(String evidence)
    {
        String normalized = evidence.toLowerCase(Locale.ROOT);
        return WEAK_EVIDENCE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static void requireRange(Object value, double min, double max, String path)
    {
        require(value instanceof Number, path + " 必须为数值");
        double number = ((Number) value).doubleValue();
        require(Double.isFinite(number) && number >= min && number <= max,
                path + " 必须在 " + min + " 到 " + max + " 之间");
    }

    private static void requireExactProperties(JSONObject object, Set<String> expected, String path)
    {
        require(object.keySet().equals(expected),
                path + " 属性必须严格为 " + expected + "，实际为 " + object.keySet());
    }

    private static void requirePropertiesSubset(JSONObject object, Set<String> allowed, String path)
    {
        require(allowed.containsAll(object.keySet()),
                path + " 包含未知属性：" + object.keySet());
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new IllegalArgumentException(message);
        }
    }
}
