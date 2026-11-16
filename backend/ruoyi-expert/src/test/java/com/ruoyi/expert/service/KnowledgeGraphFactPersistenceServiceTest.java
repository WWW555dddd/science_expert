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
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
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
        verify(graphMapper, times(2)).upsertEntity(anyMap());
        verify(graphMapper, times(2)).upsertRelation(anyMap());
        verify(graphMapper, times(4)).upsertEvidence(anyMap());
    }

    @Test
    void persistsDimensionScoresAsGraphAssessmentFacts()
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
                .thenReturn(30L, 31L);
        when(graphMapper.selectCapabilityMappings()).thenReturn(java.util.List.of(Map.of(
                "dimensionCode", "TECH_MECHANISM",
                "capabilityName", "技术机理分析")));
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode("TECH_MECHANISM");
        dimension.setDimensionName("技术机理分析");
        when(platformMapper.selectGraphDimensions(18L, "DRAFT"))
                .thenReturn(java.util.List.of(dimension));
        JSONObject analysis = JSON.parseObject("""
                {
                  "scores": {
                    "TECH_MECHANISM": {
                      "score": 86,
                      "confidence": 0.9,
                      "evidence": "第2页：负责失效机理解析"
                    }
                  }
                }
                """);

        Map<String, Object> result = persistenceService.persistDimensionAssessments(
                18L, 21L, analysis, false);

        assertEquals(1, result.get("assessmentCount"));
        assertEquals(1, result.get("capabilityLinkCount"));
        verify(graphMapper, times(2)).upsertEntity(anyMap());
        verify(graphMapper, times(2)).upsertRelation(anyMap());
        verify(graphMapper, times(2)).upsertEvidence(anyMap());
    }
}
