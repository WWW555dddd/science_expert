package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertQualityEvaluationMapper;

@ExtendWith(MockitoExtension.class)
class ExpertQualityEvaluationServiceTest
{
    @Mock
    private ExpertQualityEvaluationMapper evalMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private KnowledgeGraphQueryService graphQueryService;

    @Mock
    private ExpertDimensionViewService dimensionViewService;

    @InjectMocks
    private ExpertQualityEvaluationService evaluationService;

    @Test
    void measuresExplicitEntitiesAndDoesNotFabricateCrossMaterialConsistency()
    {
        when(evalMapper.selectCases(7L)).thenReturn(List.of(evalCase()));
        when(evalMapper.insertRun(anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> run = invocation.getArgument(0);
            run.put("runId", 21L);
            return 1;
        });
        when(platformMapper.selectMaterialList(any())).thenReturn(List.of());
        when(graphQueryService.expertSubgraph(99L, 3)).thenReturn(graph());
        when(graphQueryService.extractedEntitiesForExpert(99L))
                .thenReturn(List.of(entity("Capability", "孤立实体")));
        when(dimensionViewService.dimensions(99L, "DRAFT"))
                .thenReturn(List.of(availableDimension(), insufficientDimension()));

        Map<String, Object> run = evaluationService.run(7L, "admin");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) run.get("summary");
        assertEquals(100.0, summary.get("entityF1"));
        assertEquals(100.0, summary.get("relationEvidenceCoverage"));
        assertEquals(0.0, summary.get("evidenceLocationCoverage"));
        assertEquals(90.0, summary.get("crossMaterialConsistency"));
        assertEquals(1.0, summary.get("crossMaterialComparableDimensions"));
        assertEquals(80.45, summary.get("overallQuality"));

        ArgumentCaptor<Map<String, Object>> resultCaptor = ArgumentCaptor.forClass(Map.class);
        verify(evalMapper).insertCaseResult(resultCaptor.capture());
        JSONObject detail = JSON.parseObject(
                String.valueOf(resultCaptor.getValue().get("detailJson")));
        assertFalse(detail.getJSONArray("missingEntities").contains("Capability|孤立实体"));
    }

    @Test
    void omitsCrossMaterialConsistencyWhenNoDimensionHasTwoSources()
    {
        when(evalMapper.selectCases(8L)).thenReturn(List.of(evalCase()));
        when(evalMapper.insertRun(anyMap())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> run = invocation.getArgument(0);
            run.put("runId", 22L);
            return 1;
        });
        when(platformMapper.selectMaterialList(any())).thenReturn(List.of());
        when(graphQueryService.expertSubgraph(99L, 3)).thenReturn(graph());
        when(graphQueryService.extractedEntitiesForExpert(99L))
                .thenReturn(List.of(entity("Capability", "孤立实体")));
        when(dimensionViewService.dimensions(99L, "DRAFT"))
                .thenReturn(List.of(insufficientDimension()));

        Map<String, Object> run = evaluationService.run(8L, "admin");

        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) run.get("summary");
        assertFalse(summary.containsKey("crossMaterialConsistency"));
        assertFalse(summary.containsKey("portraitConsistency"));
        assertEquals(0.0, summary.get("crossMaterialComparableDimensions"));
    }

    private static Map<String, Object> evalCase()
    {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("caseId", 1L);
        value.put("caseName", "质量评估测试");
        value.put("userId", 99L);
        value.put("goldFieldsJson", "[]");
        value.put("goldEntitiesJson", """
                [{"entityType":"Capability","name":"孤立实体"}]
                """);
        value.put("goldRelationsJson", """
                [{"sourceType":"Expert","sourceName":"测试专家",
                  "relationType":"BELONGS_TO","targetType":"Organization",
                  "targetName":"测试单位"}]
                """);
        value.put("goldConflictsJson", "[]");
        return value;
    }

    private static Map<String, Object> graph()
    {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("sourceEntityType", "Expert");
        relation.put("sourceEntityName", "测试专家");
        relation.put("relationType", "BELONGS_TO");
        relation.put("targetEntityType", "Organization");
        relation.put("targetEntityName", "测试单位");
        relation.put("evidence", List.of(Map.of(
                "materialId", 10L,
                "sourceRef", "material:10:graph-extraction")));
        return Map.of(
                "entities", List.of(),
                "relations", List.of(relation));
    }

    private static Map<String, Object> entity(String type, String name)
    {
        return Map.of(
                "entityType", type,
                "canonicalName", name);
    }

    private static ExpertGraphDimension availableDimension()
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode("TECH_RISK");
        dimension.setDataStatus("AVAILABLE");
        dimension.setEvidenceItems(List.of(
                Map.of("score", 60, "confidence", 0.8),
                Map.of("score", 70, "confidence", 0.8)));
        return dimension;
    }

    private static ExpertGraphDimension insufficientDimension()
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode("TECH_ANALYSIS");
        dimension.setDataStatus("INSUFFICIENT_EVIDENCE");
        dimension.setEvidenceItems(List.of());
        return dimension;
    }
}
