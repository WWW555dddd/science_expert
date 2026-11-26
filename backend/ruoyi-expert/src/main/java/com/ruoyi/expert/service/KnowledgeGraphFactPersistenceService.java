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
    private static final int MATERIAL_ENTITY_LIMIT = 12;
    private static final int MATERIAL_RELATION_LIMIT = 12;

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
        graphMapper.deleteMaterialGraphEvidence(materialId);
        graphMapper.deleteOrphanMaterialRelations();
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
        Set<String> reservedCapabilityNames =
                platformMapper.selectGraphDimensions(userId, "DRAFT").stream()
                        .flatMap(item -> java.util.stream.Stream.of(
                                normalize(item.getDimensionName()),
                                normalize(item.getDimensionCode())))
                        .collect(java.util.stream.Collectors.toSet());

        JSONArray entities = facts.getJSONArray("entities");
        String reviewStatus = accepted ? "ACCEPTED" : "PENDING";
        int entityCount = persistEntities(entities, materialId, sourceKind,
                reviewStatus, entityIds, reservedCapabilityNames);
        int relationCount = persistRelations(facts.getJSONArray("relations"), expertId,
                materialId, sourceKind, reviewStatus, entityIds,
                reservedCapabilityNames, expertName);
        summary.put("entityCount", entityCount);
        summary.put("relationCount", relationCount);
        return summary;
    }

    private int persistEntities(JSONArray entities, Long materialId, String sourceKind,
            String reviewStatus, Map<String, Long> entityIds,
            Set<String> reservedCapabilityNames)
    {
        if (entities == null)
        {
            return 0;
        }
        int count = 0;
        Map<String, Integer> typeCounts = new HashMap<>();
        for (int index = 0; index < entities.size(); index++)
        {
            if (count >= MATERIAL_ENTITY_LIMIT)
            {
                break;
            }
            JSONObject entity = entities.getJSONObject(index);
            if (entity == null || !MATERIAL_ENTITY_TYPES.contains(entity.getString("entityType")))
            {
                continue;
            }
            String entityType = entity.getString("entityType");
            String name = safeName(entity.getString("name"));
            if (name == null || reservedCapability(entityType, name, reservedCapabilityNames))
            {
                continue;
            }
            int typeCount = typeCounts.getOrDefault(entityType, 0);
            if (typeCount >= entityLimit(entityType))
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
            typeCounts.put(entityType, typeCount + 1);
            count++;
        }
        return count;
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
            Map<String, Long> entityIds, Set<String> reservedCapabilityNames,
            String expertName)
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
            if ("Expert".equals(sourceType) && "Industry".equals(targetType)
                    && ("WORKS_IN_INDUSTRY".equals(relationType)
                        || "HAS_EXPERT".equals(relationType)))
            {
                String industryName = targetName;
                sourceType = "Industry";
                sourceName = industryName;
                targetType = "Expert";
                targetName = expertName;
                relationType = "HAS_EXPERT";
            }
            if (reservedCapability(sourceType, sourceName, reservedCapabilityNames)
                    || reservedCapability(targetType, targetName, reservedCapabilityNames))
            {
                continue;
            }
            if ((!MATERIAL_ENTITY_TYPES.contains(sourceType) && !"Expert".equals(sourceType))
                    || (!MATERIAL_ENTITY_TYPES.contains(targetType)
                        && !"Expert".equals(targetType))
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
            Long sourceId = resolveEntityId(sourceType, sourceName, entityIds,
                    expertId, reservedCapabilityNames);
            Long targetId = resolveEntityId(targetType, targetName, entityIds,
                    expertId, reservedCapabilityNames);
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
            if (count >= MATERIAL_RELATION_LIMIT)
            {
                break;
            }
        }
        return count;
    }

    private Long resolveEntityId(String entityType, String name,
            Map<String, Long> entityIds, Long expertId,
            Set<String> reservedCapabilityNames)
    {
        if ("Expert".equals(entityType))
        {
            return expertId;
        }
        if (!MATERIAL_ENTITY_TYPES.contains(entityType)
                || reservedCapability(entityType, name, reservedCapabilityNames))
        {
            return null;
        }
        String key = entityType + "|" + normalize(name);
        return entityIds.get(key);
    }

    private static boolean reservedCapability(String entityType, String name,
            Set<String> reservedCapabilityNames)
    {
        return "Capability".equals(entityType) && name != null
                && reservedCapabilityNames.contains(normalize(name));
    }

    private static int entityLimit(String entityType)
    {
        return switch (entityType)
        {
            case "Project" -> 3;
            case "Achievement", "Technology", "Capability" -> 2;
            default -> 1;
        };
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
