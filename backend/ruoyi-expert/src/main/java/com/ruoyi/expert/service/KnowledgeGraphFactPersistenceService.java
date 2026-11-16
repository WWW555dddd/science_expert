package com.ruoyi.expert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 将材料中的候选事实写入图谱，候选事实在人工审核前保持 PENDING。
 */
@Service
public class KnowledgeGraphFactPersistenceService
{
    private static final Set<String> MATERIAL_ENTITY_TYPES = Set.of(
            "Organization", "Credential", "Capability", "Industry", "Project",
            "Achievement", "Technology", "ServiceRegion");

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private KnowledgeGraphOntologyService ontologyService;

    @Autowired
    private KnowledgeGraphProjectionService projectionService;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Transactional
    public Map<String, Object> persist(Long userId, Long materialId, JSONObject facts,
            String sourceKind, boolean accepted)
    {
        Map<String, Object> summary = new HashMap<>();
        summary.put("entityCount", 0);
        summary.put("relationCount", 0);
        if (facts == null)
        {
            return summary;
        }
        Map<String, Object> expert = graphMapper.selectExpertEntity(userId);
        if (expert == null)
        {
            projectionService.projectExpert(userId);
            expert = graphMapper.selectExpertEntity(userId);
        }
        if (expert == null)
        {
            throw new ServiceException("专家投影失败，不能写入材料图谱事实");
        }
        Long expertId = numberValue(expert.get("entityId"));
        String expertName = String.valueOf(expert.get("canonicalName"));
        Map<String, Long> entityIds = new HashMap<>();
        entityIds.put("Expert|" + normalize(expertName), expertId);

        JSONArray entities = facts.getJSONArray("entities");
        String reviewStatus = accepted ? "ACCEPTED" : "PENDING";
        int entityCount = persistEntities(entities, materialId, sourceKind,
                reviewStatus, entityIds);
        int relationCount = persistRelations(facts.getJSONArray("relations"), expertId,
                materialId, sourceKind, reviewStatus, entityIds);
        summary.put("entityCount", entityCount);
        summary.put("relationCount", relationCount);
        return summary;
    }

    /**
     * 将模型给出的维度评分声明保存为图谱事实。
     *
     * <p>每条声明独立对应材料、评估实体和证据，画像聚合只读取这些图谱事实。
     * 不直接在聚合服务中消费模型响应，避免画像和图谱形成两套平行数据源。</p>
     */
    @Transactional
    public Map<String, Object> persistDimensionAssessments(Long userId, Long materialId,
            JSONObject analysis, boolean accepted)
    {
        Map<String, Object> summary = new HashMap<>();
        summary.put("assessmentCount", 0);
        summary.put("capabilityLinkCount", 0);
        JSONObject scores = analysis == null ? null : analysis.getJSONObject("scores");
        if (scores == null || scores.isEmpty())
        {
            return summary;
        }
        Map<String, Object> expert = graphMapper.selectExpertEntity(userId);
        if (expert == null)
        {
            projectionService.projectExpert(userId);
            expert = graphMapper.selectExpertEntity(userId);
        }
        if (expert == null)
        {
            throw new ServiceException("专家投影失败，不能写入画像图谱声明");
        }

        graphMapper.deleteMaterialAssessments(materialId);
        graphMapper.deleteMaterialAssessmentRelations(materialId);
        graphMapper.deleteMaterialAssessmentEntities(materialId);

        String reviewStatus = accepted ? "ACCEPTED" : "PENDING";
        Long expertId = numberValue(expert.get("entityId"));
        Map<String, String> capabilityNames = new HashMap<>();
        for (Map<String, Object> mapping : graphMapper.selectCapabilityMappings())
        {
            capabilityNames.put(String.valueOf(mapping.get("dimensionCode")),
                    String.valueOf(mapping.get("capabilityName")));
        }
        Map<String, String> dimensionNames = new HashMap<>();
        for (ExpertGraphDimension dimension :
                platformMapper.selectGraphDimensions(userId, "DRAFT"))
        {
            dimensionNames.put(dimension.getDimensionCode(), dimension.getDimensionName());
        }

        int assessmentCount = 0;
        int capabilityLinkCount = 0;
        for (Map.Entry<String, Object> entry : scores.entrySet())
        {
            String dimensionCode = entry.getKey();
            if (!(entry.getValue() instanceof JSONObject score))
            {
                continue;
            }
            Object scoreValue = score.get("score");
            Object confidenceValue = score.get("confidence");
            String evidence = score.getString("evidence");
            if (!(scoreValue instanceof Number) || !(confidenceValue instanceof Number)
                    || StringUtils.isEmpty(evidence))
            {
                continue;
            }
            double scoreNumber = clamp(((Number) scoreValue).doubleValue(), 0, 100);
            double confidence = clamp(((Number) confidenceValue).doubleValue(), 0, 1);
            String dimensionName = dimensionNames.getOrDefault(dimensionCode, dimensionCode);
            String capabilityName = capabilityNames.getOrDefault(dimensionCode, dimensionName);

            Map<String, Object> capabilityProperties = new LinkedHashMap<>();
            capabilityProperties.put("name", capabilityName);
            capabilityProperties.put("dimensionCode", dimensionCode);
            Long capabilityId = upsertEntity("Capability",
                    "capability:" + sha256(normalize(capabilityName)),
                    capabilityName, capabilityProperties, reviewStatus);

            Map<String, Object> assessmentProperties = new LinkedHashMap<>();
            assessmentProperties.put("dimensionCode", dimensionCode);
            assessmentProperties.put("dimensionName", dimensionName);
            assessmentProperties.put("materialId", materialId);
            assessmentProperties.put("score", scoreNumber);
            assessmentProperties.put("confidence", confidence);
            assessmentProperties.put("ruleVersion", "graph-profile-v1");
            Long assessmentId = upsertEntity("CapabilityAssessment",
                    "assessment:" + materialId + ":" + dimensionCode,
                    dimensionName + "·材料" + materialId,
                    assessmentProperties, reviewStatus);

            linkAssessment(expertId, "Expert", "ASSESSED_AS",
                    "CapabilityAssessment", assessmentId, reviewStatus,
                    scoreNumber, confidence, materialId,
                    "material:" + materialId + ":dimension:" + dimensionCode,
                    evidence, assessmentId);
            linkAssessment(assessmentId, "CapabilityAssessment", "ASSESSES_CAPABILITY",
                    "Capability", capabilityId, reviewStatus,
                    scoreNumber, confidence, materialId,
                    "material:" + materialId + ":dimension:" + dimensionCode,
                    "维度“" + dimensionName + "”映射到能力“" + capabilityName + "”",
                    capabilityId);
            assessmentCount++;
            capabilityLinkCount++;
        }
        summary.put("assessmentCount", assessmentCount);
        summary.put("capabilityLinkCount", capabilityLinkCount);
        return summary;
    }

    private int persistEntities(JSONArray entities, Long materialId, String sourceKind,
            String reviewStatus, Map<String, Long> entityIds)
    {
        if (entities == null)
        {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < entities.size(); index++)
        {
            JSONObject entity = entities.getJSONObject(index);
            if (entity == null || !MATERIAL_ENTITY_TYPES.contains(entity.getString("entityType")))
            {
                continue;
            }
            String entityType = entity.getString("entityType");
            String name = safeName(entity.getString("name"));
            if (name == null)
            {
                continue;
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", name);
            properties.put("materialId", materialId);
            properties.put("extractionSource", sourceKind);
            Long entityId = upsertEntity(entityType, entityKey(entityType, name), name,
                    properties, reviewStatus);
            entityIds.put(entityType + "|" + normalize(name), entityId);
            persistEntityEvidence(entityId, materialId, sourceKind, entity);
            count++;
        }
        return count;
    }

    private void linkAssessment(Long sourceId, String sourceType, String relationType,
            String targetType, Long targetId, String reviewStatus, double score,
            double confidence, Long materialId, String sourceRef, String excerpt,
            Long evidenceEntityId)
    {
        ontologyService.requireRelationType(relationType, sourceType, targetType);
        Map<String, Object> relationRow = new LinkedHashMap<>();
        relationRow.put("sourceEntityId", sourceId);
        relationRow.put("relationType", relationType);
        relationRow.put("targetEntityId", targetId);
        relationRow.put("confidence", confidence);
        relationRow.put("reviewStatus", reviewStatus);
        relationRow.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        relationRow.put("sourceKind", "GRAPH_PROFILE");
        relationRow.put("propertiesJson", JSON.toJSONString(Map.of(
                "score", score,
                "materialId", materialId,
                "ruleVersion", "graph-profile-v1")));
        graphMapper.upsertRelation(relationRow);
        Long relationId = graphMapper.selectRelationId(sourceId, relationType, targetId);

        Map<String, Object> evidenceRow = new LinkedHashMap<>();
        evidenceRow.put("evidenceKey", sha256(String.join("|",
                String.valueOf(relationId), "GRAPH_PROFILE", sourceRef)));
        evidenceRow.put("relationId", relationId);
        evidenceRow.put("entityId", evidenceEntityId);
        evidenceRow.put("sourceType", "MATERIAL");
        evidenceRow.put("sourceRef", sourceRef);
        evidenceRow.put("materialId", materialId);
        evidenceRow.put("pageNo", 1);
        evidenceRow.put("charStart", null);
        evidenceRow.put("charEnd", null);
        evidenceRow.put("quoteHash", sha256(excerpt));
        evidenceRow.put("excerpt", abbreviate(excerpt, 800));
        evidenceRow.put("confidence", confidence);
        evidenceRow.put("extractorVersion", "graph-profile-v1");
        graphMapper.upsertEvidence(evidenceRow);
    }

    private void persistEntityEvidence(Long entityId, Long materialId, String sourceKind,
            JSONObject entity)
    {
        String evidence = entity.getString("evidence");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("evidenceKey", sha256(String.join("|", "ENTITY",
                String.valueOf(entityId), "MATERIAL", String.valueOf(materialId), evidence)));
        row.put("relationId", null);
        row.put("entityId", entityId);
        row.put("sourceType", "MATERIAL");
        row.put("sourceRef", "material:" + materialId + ":graph-extraction");
        row.put("materialId", materialId);
        row.put("pageNo", 1);
        row.put("charStart", entity.getInteger("charStart"));
        row.put("charEnd", entity.getInteger("charEnd"));
        row.put("quoteHash", sha256(evidence));
        row.put("excerpt", abbreviate(evidence, 800));
        row.put("confidence", confidence(entity));
        row.put("extractorVersion", sourceKind + "-graph-v1");
        graphMapper.upsertEvidence(row);
    }

    private int persistRelations(JSONArray relations, Long expertId,
            Long materialId, String sourceKind, String reviewStatus,
            Map<String, Long> entityIds)
    {
        if (relations == null)
        {
            return 0;
        }
        int count = 0;
        for (int index = 0; index < relations.size(); index++)
        {
            JSONObject relation = relations.getJSONObject(index);
            if (relation == null)
            {
                continue;
            }
            String sourceType = relation.getString("sourceType");
            String targetType = relation.getString("targetType");
            String relationType = relation.getString("relationType");
            String sourceName = safeName(relation.getString("sourceName"));
            String targetName = safeName(relation.getString("targetName"));
            if ((!MATERIAL_ENTITY_TYPES.contains(sourceType) && !"Expert".equals(sourceType))
                    || !MATERIAL_ENTITY_TYPES.contains(targetType)
                    || StringUtils.isEmpty(relationType)
                    || sourceName == null || targetName == null)
            {
                continue;
            }
            try
            {
                ontologyService.requireRelationType(relationType, sourceType, targetType);
            }
            catch (ServiceException ex)
            {
                // 单条关系方向不合法时忽略该关系，不丢弃同批其他有效事实和画像。
                continue;
            }
            Long sourceId = resolveEntityId(sourceType, sourceName, materialId,
                    sourceKind, reviewStatus, entityIds, expertId);
            Long targetId = resolveEntityId(targetType, targetName, materialId,
                    sourceKind, reviewStatus, entityIds, expertId);
            if (sourceId == null || targetId == null || sourceId.equals(targetId))
            {
                continue;
            }

            Map<String, Object> relationRow = new LinkedHashMap<>();
            relationRow.put("sourceEntityId", sourceId);
            relationRow.put("relationType", relationType);
            relationRow.put("targetEntityId", targetId);
            relationRow.put("confidence", confidence(relation));
            relationRow.put("reviewStatus", reviewStatus);
            relationRow.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
            relationRow.put("sourceKind", sourceKind);
            relationRow.put("propertiesJson", JSON.toJSONString(Map.of(
                    "materialId", materialId,
                    "extractionSource", sourceKind)));
            graphMapper.upsertRelation(relationRow);
            Long relationId = graphMapper.selectRelationId(sourceId, relationType, targetId);

            String evidence = relation.getString("evidence");
            Map<String, Object> evidenceRow = new LinkedHashMap<>();
            evidenceRow.put("evidenceKey", sha256(String.join("|",
                    String.valueOf(relationId), "MATERIAL", String.valueOf(materialId),
                    evidence)));
            evidenceRow.put("relationId", relationId);
            evidenceRow.put("entityId", null);
            evidenceRow.put("sourceType", "MATERIAL");
            evidenceRow.put("sourceRef", "material:" + materialId + ":graph-extraction");
            evidenceRow.put("materialId", materialId);
            evidenceRow.put("pageNo", 1);
            evidenceRow.put("charStart", relation.getInteger("charStart"));
            evidenceRow.put("charEnd", relation.getInteger("charEnd"));
            evidenceRow.put("quoteHash", sha256(evidence));
            evidenceRow.put("excerpt", abbreviate(evidence, 800));
            evidenceRow.put("confidence", confidence(relation));
            evidenceRow.put("extractorVersion", sourceKind + "-graph-v1");
            graphMapper.upsertEvidence(evidenceRow);
            count++;
        }
        return count;
    }

    private Long resolveEntityId(String entityType, String name, Long materialId,
            String sourceKind, String reviewStatus, Map<String, Long> entityIds,
            Long expertId)
    {
        if ("Expert".equals(entityType))
        {
            return expertId;
        }
        if (!MATERIAL_ENTITY_TYPES.contains(entityType))
        {
            return null;
        }
        String key = entityType + "|" + normalize(name);
        Long existing = entityIds.get(key);
        if (existing != null)
        {
            return existing;
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", name);
        properties.put("materialId", materialId);
        properties.put("extractionSource", sourceKind);
        Long entityId = upsertEntity(entityType, entityKey(entityType, name),
                name, properties, reviewStatus);
        entityIds.put(key, entityId);
        return entityId;
    }

    private Long upsertEntity(String entityType, String externalKey, String name,
            Map<String, Object> properties, String reviewStatus)
    {
        ontologyService.requireEntityType(entityType);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entityType", entityType);
        row.put("externalKey", externalKey);
        row.put("canonicalName", name);
        row.put("normalizedName", normalize(name));
        row.put("propertiesJson", JSON.toJSONString(properties));
        row.put("reviewStatus", reviewStatus);
        row.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        graphMapper.upsertEntity(row);
        Long entityId = graphMapper.selectEntityIdByExternalKey(entityType, externalKey);
        if (entityId == null)
        {
            throw new ServiceException("材料图谱实体写入失败：" + entityType + "/" + name);
        }
        return entityId;
    }

    private static double confidence(JSONObject fact)
    {
        double value = fact.getDoubleValue("confidence");
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static String entityKey(String entityType, String name)
    {
        return entityType.toLowerCase(Locale.ROOT) + ":" + sha256(normalize(name));
    }

    private static String normalize(String value)
    {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）()【】\\[\\]]+", "");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private static String safeName(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String name = value.trim();
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String abbreviate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Long numberValue(Object value)
    {
        return value instanceof Number
                ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
