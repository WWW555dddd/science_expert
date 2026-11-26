package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphFactPersistenceServiceTest
{
    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @Spy
    private KnowledgeGraphOntologyService ontologyService;

    @InjectMocks
    private KnowledgeGraphFactPersistenceService persistenceService;

    @Test
    void persistsMaterialFactsAsPendingWithEvidence()
    {
        when(graphMapper.selectExpertEntity(12L)).thenReturn(Map.of(
                "entityId", 1L,
                "canonicalName", "李专家"));
        Map<String, Long> ids = new ConcurrentHashMap<>();
        AtomicLong nextId = new AtomicLong(1);
        when(graphMapper.selectEntityIdByExternalKey(anyString(), anyString()))
                .thenAnswer(invocation -> ids.computeIfAbsent(
                        invocation.getArgument(0) + "|" + invocation.getArgument(1),
                        ignored -> nextId.incrementAndGet()));
        when(graphMapper.selectRelationId(anyLong(), anyString(), anyLong()))
                .thenReturn(10L, 11L);
        when(platformMapper.selectGraphDimensions(12L, "DRAFT"))
                .thenReturn(java.util.List.of());

        JSONObject facts = JSON.parseObject("""
                {
                  "entities": [
                    {"entityType":"Project","name":"智能装备项目","confidence":0.9,
                     "evidence":"项目：智能装备项目"},
                    {"entityType":"Achievement","name":"发明专利A","confidence":0.9,
                     "evidence":"成果：发明专利A"}
                  ],
                  "relations": [
                    {"sourceType":"Expert","sourceName":"李专家",
                     "relationType":"PARTICIPATED_IN","targetType":"Project",
                     "targetName":"智能装备项目","confidence":0.9,
                     "evidence":"项目：智能装备项目","charStart":3,"charEnd":13},
                    {"sourceType":"Expert","sourceName":"李专家",
                     "relationType":"PRODUCED","targetType":"Achievement",
                     "targetName":"发明专利A","confidence":0.9,
                     "evidence":"成果：发明专利A","charStart":18,"charEnd":26}
                  ]
                }
                """);

        Map<String, Object> result = persistenceService.persist(
                12L, 20L, facts, "RULE", false);

        assertEquals(2, result.get("entityCount"));
        assertEquals(2, result.get("relationCount"));
        verify(graphMapper).deleteMaterialGraphEvidence(20L);
        verify(graphMapper).deleteOrphanMaterialRelations();
        verify(graphMapper, times(2)).upsertEntity(anyMap());
        verify(graphMapper, times(2)).upsertRelation(anyMap());
        verify(graphMapper, times(4)).upsertEvidence(anyMap());
    }

    @Test
    void convertsIndustryFactsToIndustryCenteredExpertRelation()
    {
        when(graphMapper.selectExpertEntity(18L)).thenReturn(Map.of(
                "entityId", 5L,
                "canonicalName", "张专家"));
        Map<String, Long> ids = new ConcurrentHashMap<>();
        AtomicLong nextId = new AtomicLong(20);
        when(graphMapper.selectEntityIdByExternalKey(anyString(), anyString()))
                .thenAnswer(invocation -> ids.computeIfAbsent(
                        invocation.getArgument(0) + "|" + invocation.getArgument(1),
                        ignored -> nextId.incrementAndGet()));
        when(graphMapper.selectRelationId(anyLong(), anyString(), anyLong()))
                .thenReturn(30L);
        when(platformMapper.selectGraphDimensions(18L, "DRAFT"))
                .thenReturn(java.util.List.of());
        JSONObject facts = JSON.parseObject("""
                {
                  "entities": [
                    {"entityType":"Industry","name":"新能源","confidence":0.9,
                     "evidence":"产业领域：新能源"}
                  ],
                  "relations": [
                    {"sourceType":"Expert","sourceName":"张专家",
                     "relationType":"WORKS_IN_INDUSTRY","targetType":"Industry",
                     "targetName":"新能源","confidence":0.9,
                     "evidence":"产业领域：新能源"}
                  ]
                }
                """);

        Map<String, Object> result = persistenceService.persist(
                18L, 21L, facts, "RULE", false);

        assertEquals(1, result.get("entityCount"));
        assertEquals(1, result.get("relationCount"));
        ArgumentCaptor<Map<String, Object>> relationCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(graphMapper).upsertRelation(relationCaptor.capture());
        assertEquals("HAS_EXPERT", relationCaptor.getValue().get("relationType"));
        assertEquals(21L, relationCaptor.getValue().get("sourceEntityId"));
        assertEquals(5L, relationCaptor.getValue().get("targetEntityId"));
        verify(graphMapper, times(2)).upsertEvidence(anyMap());
    }

    @Test
    void limitsMaterialGraphFactsToImportantNodesPerType()
    {
        when(graphMapper.selectExpertEntity(30L)).thenReturn(Map.of(
                "entityId", 5L,
                "canonicalName", "王专家"));
        Map<String, Long> ids = new ConcurrentHashMap<>();
        AtomicLong nextId = new AtomicLong(40);
        when(graphMapper.selectEntityIdByExternalKey(anyString(), anyString()))
                .thenAnswer(invocation -> ids.computeIfAbsent(
                        invocation.getArgument(0) + "|" + invocation.getArgument(1),
                        ignored -> nextId.incrementAndGet()));
        when(platformMapper.selectGraphDimensions(30L, "DRAFT"))
                .thenReturn(java.util.List.of());
        JSONObject facts = JSON.parseObject("""
                {
                  "entities": [
                    {"entityType":"Project","name":"项目A","confidence":0.9,"evidence":"项目A"},
                    {"entityType":"Project","name":"项目B","confidence":0.9,"evidence":"项目B"},
                    {"entityType":"Project","name":"项目C","confidence":0.9,"evidence":"项目C"},
                    {"entityType":"Project","name":"项目D","confidence":0.9,"evidence":"项目D"}
                  ],
                  "relations": []
                }
                """);

        Map<String, Object> result = persistenceService.persist(
                30L, 31L, facts, "LLM", false);

        assertEquals(3, result.get("entityCount"));
        verify(graphMapper, times(3)).upsertEntity(anyMap());
        verify(graphMapper, times(3)).upsertEvidence(anyMap());
    }
}
