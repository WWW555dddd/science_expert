package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * MySQL 图谱源数据查询。
 */
@Service
public class KnowledgeGraphQueryService
{
    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private KnowledgeGraphOntologyService ontologyService;

    public Map<String, Object> ontology()
    {
        Map<String, Object> registered =
                graphMapper.selectOntologyVersion(KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        if (registered == null)
        {
            throw new ServiceException("知识图谱版本尚未注册，请执行 backend/sql/init.sql");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("registered", registered);
        result.put("schema", ontologyService.getOntology());
        result.put("capabilityMappings", graphMapper.selectCapabilityMappings());
        return result;
    }

    public Map<String, Object> expertSubgraph(Long userId, int depth)
    {
        Map<String, Object> seed = graphMapper.selectExpertEntity(userId);
        if (seed == null)
        {
            Map<String, Object> missing = new HashMap<>();
            missing.put("status", "NOT_PROJECTED");
            missing.put("userId", userId);
            missing.put("message", "该专家尚未投影到知识图谱");
            missing.put("entities", List.of());
            missing.put("relations", List.of());
            return missing;
        }

        int safeDepth = Math.max(1, Math.min(depth, 3));
        Long seedId = numberValue(seed.get("entityId"));
        Set<Long> allEntityIds = new LinkedHashSet<>();
        Set<Long> frontier = new LinkedHashSet<>();
        allEntityIds.add(seedId);
        frontier.add(seedId);
        Map<Long, Map<String, Object>> relations = new LinkedHashMap<>();

        for (int level = 0; level < safeDepth && !frontier.isEmpty(); level++)
        {
            List<Map<String, Object>> levelRows =
                    graphMapper.selectRelationsForEntityIds(new ArrayList<>(frontier));
            Set<Long> nextFrontier = new LinkedHashSet<>();
            for (Map<String, Object> row : levelRows)
            {
                Long relationId = numberValue(row.get("relationId"));
                Long sourceId = numberValue(row.get("sourceEntityId"));
                Long targetId = numberValue(row.get("targetEntityId"));
                String sourceType = String.valueOf(row.get("sourceEntityType"));
                String targetType = String.valueOf(row.get("targetEntityType"));
                boolean otherExpert = ("Expert".equals(sourceType) && !seedId.equals(sourceId))
                        || ("Expert".equals(targetType) && !seedId.equals(targetId));
                if (otherExpert)
                {
                    continue;
                }
                if (!relations.containsKey(relationId))
                {
                    relations.put(relationId, relationView(row));
                }
                if (!allEntityIds.contains(sourceId))
                {
                    allEntityIds.add(sourceId);
                    nextFrontier.add(sourceId);
                }
                if (!allEntityIds.contains(targetId))
                {
                    allEntityIds.add(targetId);
                    nextFrontier.add(targetId);
                }
            }
            frontier = nextFrontier;
        }

        List<Map<String, Object>> entities = graphMapper.selectEntitiesByIds(
                new ArrayList<>(allEntityIds)).stream()
                .map(KnowledgeGraphQueryService::entityView)
                .collect(Collectors.toCollection(ArrayList::new));
        attachEntityEvidence(entities);
        attachEvidence(relations);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "READY");
        result.put("userId", userId);
        result.put("seedEntityId", seedId);
        result.put("depth", safeDepth);
        result.put("ontologyVersion", seed.get("ontologyVersion"));
        result.put("entityCount", entities.size());
        result.put("relationCount", relations.size());
        result.put("entities", entities);
        result.put("relations", new ArrayList<>(relations.values()));
        return result;
    }

    public Map<String, Object> taskSubgraph(Long taskEntityId, int depth)
    {
        Map<String, Object> seed = graphMapper.selectEntityById(taskEntityId);
        if (seed == null || !"Task".equals(seed.get("entityType")))
        {
            Map<String, Object> missing = new HashMap<>();
            missing.put("status", "NOT_FOUND");
            missing.put("taskEntityId", taskEntityId);
            missing.put("entities", List.of());
            missing.put("relations", List.of());
            return missing;
        }
        int safeDepth = Math.max(1, Math.min(depth, 3));
        Set<Long> allEntityIds = new LinkedHashSet<>();
        Set<Long> frontier = new LinkedHashSet<>();
        allEntityIds.add(taskEntityId);
        frontier.add(taskEntityId);
        Map<Long, Map<String, Object>> relations = new LinkedHashMap<>();

        for (int level = 0; level < safeDepth && !frontier.isEmpty(); level++)
        {
            List<Map<String, Object>> levelRows =
                    graphMapper.selectRelationsForEntityIds(new ArrayList<>(frontier));
            Set<Long> nextFrontier = new LinkedHashSet<>();
            for (Map<String, Object> row : levelRows)
            {
                Long relationId = numberValue(row.get("relationId"));
                Long sourceId = numberValue(row.get("sourceEntityId"));
                Long targetId = numberValue(row.get("targetEntityId"));
                relations.putIfAbsent(relationId, relationView(row));
                if (allEntityIds.add(sourceId))
                {
                    nextFrontier.add(sourceId);
                }
                if (allEntityIds.add(targetId))
                {
                    nextFrontier.add(targetId);
                }
            }
            frontier = nextFrontier;
        }
        List<Map<String, Object>> entities = graphMapper.selectEntitiesByIds(
                new ArrayList<>(allEntityIds)).stream()
                .map(KnowledgeGraphQueryService::entityView)
                .collect(Collectors.toCollection(ArrayList::new));
        attachEntityEvidence(entities);
        attachEvidence(relations);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "READY");
        result.put("taskEntityId", taskEntityId);
        result.put("seedEntityId", taskEntityId);
        result.put("depth", safeDepth);
        result.put("ontologyVersion", seed.get("ontologyVersion"));
        result.put("entityCount", entities.size());
        result.put("relationCount", relations.size());
        result.put("entities", entities);
        result.put("relations", new ArrayList<>(relations.values()));
        return result;
    }

    public List<Map<String, Object>> evidenceForRelations(List<Long> relationIds)
    {
        return relationIds == null || relationIds.isEmpty()
                ? List.of() : graphMapper.selectEvidenceByRelationIds(relationIds);
    }

    public List<Map<String, Object>> evidenceForEntities(List<Long> entityIds)
    {
        return entityIds == null || entityIds.isEmpty()
                ? List.of() : graphMapper.selectEvidenceByEntityIds(entityIds);
    }

    public List<Map<String, Object>> extractedEntitiesForExpert(Long userId)
    {
        return graphMapper.selectExtractedEntitiesByUserId(userId).stream()
                .map(KnowledgeGraphQueryService::entityView)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void attachEvidence(Map<Long, Map<String, Object>> relations)
    {
        if (relations.isEmpty())
        {
            return;
        }
        List<Map<String, Object>> evidenceRows = graphMapper.selectEvidenceByRelationIds(
                new ArrayList<>(relations.keySet()));
        for (Map<String, Object> evidenceRow : evidenceRows)
        {
            Long relationId = numberValue(evidenceRow.get("relationId"));
            Map<String, Object> relation = relations.get(relationId);
            if (relation == null)
            {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evidenceList =
                    (List<Map<String, Object>>) relation.computeIfAbsent("evidence",
                            key -> new ArrayList<Map<String, Object>>());
            evidenceList.add(new LinkedHashMap<>(evidenceRow));
        }
    }

    private void attachEntityEvidence(List<Map<String, Object>> entities)
    {
        if (entities.isEmpty())
        {
            return;
        }
        Map<Long, Map<String, Object>> byId = entities.stream().collect(Collectors.toMap(
                entity -> numberValue(entity.get("entityId")),
                entity -> entity));
        List<Map<String, Object>> evidenceRows = graphMapper.selectEvidenceByEntityIds(
                new ArrayList<>(byId.keySet()));
        for (Map<String, Object> evidenceRow : evidenceRows)
        {
            Map<String, Object> entity = byId.get(numberValue(evidenceRow.get("entityId")));
            if (entity == null)
            {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evidenceList =
                    (List<Map<String, Object>>) entity.computeIfAbsent("evidence",
                            key -> new ArrayList<Map<String, Object>>());
            evidenceList.add(new LinkedHashMap<>(evidenceRow));
        }
    }

    private static Map<String, Object> entityView(Map<String, Object> row)
    {
        Map<String, Object> entity = new LinkedHashMap<>(row);
        Object properties = entity.remove("propertiesJson");
        entity.put("properties", parseProperties(properties));
        return entity;
    }

    private static Map<String, Object> relationView(Map<String, Object> row)
    {
        Map<String, Object> relation = new LinkedHashMap<>(row);
        Object properties = relation.remove("propertiesJson");
        relation.put("properties", parseProperties(properties));
        relation.put("evidence", new ArrayList<>());
        return relation;
    }

    private static Object parseProperties(Object value)
    {
        if (value == null)
        {
            return Map.of();
        }
        if (value instanceof Map<?, ?>)
        {
            return value;
        }
        try
        {
            return JSON.parse(value.toString());
        }
        catch (Exception ex)
        {
            return Map.of("raw", value.toString());
        }
    }

    private static Long numberValue(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }
}
