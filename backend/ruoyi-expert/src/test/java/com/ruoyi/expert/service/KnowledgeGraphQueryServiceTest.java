package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphQueryServiceTest
{
    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Spy
    private KnowledgeGraphOntologyService ontologyService;

    @InjectMocks
    private KnowledgeGraphQueryService queryService;

    @Test
    void traversesTwoHopsAndAttachesEvidence()
    {
        when(graphMapper.selectExpertEntity(12L)).thenReturn(entity(
                1L, "Expert", "李专家", null));
        when(graphMapper.selectRelationsForEntityIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            if (ids.contains(1L))
            {
                return List.of(relation(10L, 1L, "Expert", "李专家",
                        "BELONGS_TO", 2L, "Organization", "某研究院"));
            }
            if (ids.contains(2L))
            {
                return List.of(relation(11L, 2L, "Organization", "某研究院",
                        "RELATED_TO", 3L, "Industry", "新材料"));
            }
            return List.of();
        });
        when(graphMapper.selectEntitiesByIds(anyList())).thenReturn(List.of(
                entity(1L, "Expert", "李专家", null),
                entity(2L, "Organization", "某研究院", null),
                entity(3L, "Industry", "新材料", null)));
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceId", 20L);
        evidence.put("relationId", 10L);
        evidence.put("sourceType", "EXPERT_PROFILE");
        evidence.put("sourceRef", "expert_profile:4:organization");
        when(graphMapper.selectEvidenceByRelationIds(anyList())).thenReturn(List.of(evidence));
        Map<String, Object> entityEvidence = new LinkedHashMap<>(evidence);
        entityEvidence.put("evidenceId", 21L);
        entityEvidence.put("relationId", null);
        entityEvidence.put("entityId", 1L);
        when(graphMapper.selectEvidenceByEntityIds(anyList()))
                .thenReturn(List.of(entityEvidence));

        Map<String, Object> result = queryService.expertSubgraph(12L, 2);

        assertEquals("READY", result.get("status"));
        assertEquals(2, result.get("depth"));
        assertEquals(3, result.get("entityCount"));
        assertEquals(2, result.get("relationCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> relations = (List<Map<String, Object>>) result.get("relations");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> attached =
                (List<Map<String, Object>>) relations.get(0).get("evidence");
        assertEquals(1, attached.size());
        assertTrue(attached.get(0).containsKey("sourceRef"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entities =
                (List<Map<String, Object>>) result.get("entities");
        assertTrue(entities.stream().anyMatch(item ->
                item.get("entityId").equals(1L) && item.containsKey("evidence")));
    }

    @Test
    void doesNotTraverseThroughAnotherExpert()
    {
        when(graphMapper.selectExpertEntity(12L)).thenReturn(entity(
                1L, "Expert", "李专家", null));
        when(graphMapper.selectRelationsForEntityIds(anyList())).thenAnswer(invocation -> {
            List<Long> ids = invocation.getArgument(0);
            if (ids.contains(1L))
            {
                return List.of(relation(30L, 1L, "Expert", "李专家",
                        "BELONGS_TO", 2L, "Organization", "某研究院"));
            }
            if (ids.contains(2L))
            {
                return List.of(relation(31L, 2L, "Organization", "某研究院",
                        "BELONGS_TO", 3L, "Expert", "王专家"));
            }
            return List.of();
        });
        when(graphMapper.selectEntitiesByIds(anyList())).thenReturn(List.of(
                entity(1L, "Expert", "李专家", null),
                entity(2L, "Organization", "某研究院", null)));
        when(graphMapper.selectEvidenceByRelationIds(anyList())).thenReturn(List.of());
        when(graphMapper.selectEvidenceByEntityIds(anyList())).thenReturn(List.of());

        Map<String, Object> result = queryService.expertSubgraph(12L, 3);

        assertEquals(2, result.get("entityCount"));
        assertEquals(1, result.get("relationCount"));
    }

    @Test
    void buildsAllExpertOverviewAndCountsSharedGroupEntities()
    {
        when(graphMapper.selectOverviewEntities(400)).thenReturn(List.of(
                entity(10L, "Industry", "人工智能", null),
                entity(11L, "Industry", "新能源", null),
                entity(20L, "Expert", "李专家", null),
                entity(21L, "Expert", "王专家", null),
                entity(30L, "Project", "联合项目", null)));
        when(graphMapper.selectOverviewRelations(800)).thenReturn(List.of(
                relation(100L, 10L, "Industry", "人工智能",
                        "HAS_EXPERT", 20L, "Expert", "李专家"),
                relation(101L, 10L, "Industry", "人工智能",
                        "HAS_EXPERT", 21L, "Expert", "王专家"),
                relation(102L, 11L, "Industry", "新能源",
                        "HAS_EXPERT", 21L, "Expert", "王专家"),
                relation(103L, 20L, "Expert", "李专家",
                        "PARTICIPATED_IN", 30L, "Project", "联合项目"),
                relation(104L, 21L, "Expert", "王专家",
                        "PARTICIPATED_IN", 30L, "Project", "联合项目")));

        Map<String, Object> result = queryService.overviewSubgraph(400, 800);

        assertEquals(2L, result.get("expertCount"));
        assertEquals(2L, result.get("industryCount"));
        assertEquals(5, result.get("entityCount"));
        assertEquals(5, result.get("relationCount"));
        assertEquals(2L, result.get("sharedEntityCount"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups =
                (List<Map<String, Object>>) result.get("industryGroups");
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).get("expertCount"));
    }

    private static Map<String, Object> entity(Long id, String type, String name,
            String propertiesJson)
    {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", id);
        entity.put("entityType", type);
        entity.put("canonicalName", name);
        entity.put("propertiesJson", propertiesJson);
        entity.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        return entity;
    }

    private static Map<String, Object> relation(Long relationId, Long sourceId,
            String sourceType, String sourceName, String relationType, Long targetId,
            String targetType, String targetName)
    {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("relationId", relationId);
        relation.put("sourceEntityId", sourceId);
        relation.put("sourceEntityType", sourceType);
        relation.put("sourceEntityName", sourceName);
        relation.put("relationType", relationType);
        relation.put("targetEntityId", targetId);
        relation.put("targetEntityType", targetType);
        relation.put("targetEntityName", targetName);
        relation.put("propertiesJson", "{}");
        return relation;
    }
}
