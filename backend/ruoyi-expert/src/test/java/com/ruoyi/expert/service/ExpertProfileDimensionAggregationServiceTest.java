package com.ruoyi.expert.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class ExpertProfileDimensionAggregationServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private KnowledgeGraphMapper graphMapper;

    @InjectMocks
    private ExpertProfileDimensionAggregationService aggregationService;

    @Test
    void aggregatesAcceptedGraphAssessmentsAndMarksLargeScoreGapAsConflict()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setGraphVersion(1);
        when(platformMapper.selectGraphDimensions(12L, "DRAFT"))
                .thenReturn(List.of(dimension("A"), dimension("B")));
        when(graphMapper.selectDimensionAssessments(12L, false))
                .thenReturn(List.of(
                        assessment("A", 10L, 60, 0.8, 100L, 200L, 300L),
                        assessment("A", 11L, 90, 0.8, 101L, 201L, 301L),
                        assessment("B", 11L, 75, 0.7, 102L, 202L, 302L)));

        aggregationService.rebuildDraft(profile);

        verify(platformMapper).deleteDraftDimensionEvidence(12L);
        verify(platformMapper, times(3)).insertDimensionEvidence(anyMap());
        verify(platformMapper).deleteDraftGraph(12L);
        verify(platformMapper).insertGraphScore(12L, "A", 75.0, 0.8,
                anyString(), "DRAFT", 1, "GRAPH_PROFILE", "1",
                "GRAPH_ASSESSMENT_AGGREGATE", 2, "100,101", "200,201", "300,301",
                2, "graph-profile-v1");
        verify(platformMapper).insertGraphScore(12L, "B", 75.0, 0.7,
                anyString(), "DRAFT", 1, "GRAPH_PROFILE", "0",
                "GRAPH_ASSESSMENT_AGGREGATE", 1, "102", "202", "302",
                1, "graph-profile-v1");
    }

    @Test
    void acceptedOnlyRebuildPassesReviewFilterToGraphSource()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(15L);
        when(platformMapper.selectGraphDimensions(15L, "DRAFT")).thenReturn(List.of());
        when(graphMapper.selectDimensionAssessments(15L, true)).thenReturn(List.of());

        aggregationService.rebuildDraft(profile, true);

        verify(graphMapper).selectDimensionAssessments(15L, true);
    }

    private static ExpertGraphDimension dimension(String code)
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode(code);
        return dimension;
    }

    private static Map<String, Object> assessment(String code, Long materialId,
            double score, double confidence, Long relationId, Long entityId, Long evidenceId)
    {
        JSONObject properties = new JSONObject();
        properties.put("dimensionCode", code);
        properties.put("score", score);
        properties.put("confidence", confidence);
        return Map.of(
                "dimensionCode", code,
                "materialId", materialId,
                "score", score,
                "confidence", confidence,
                "propertiesJson", properties.toJSONString(),
                "evidence", "图谱材料证据",
                "relationId", relationId,
                "assessmentEntityId", entityId,
                "evidenceId", evidenceId,
                "fileName", "material-" + materialId + ".pdf");
    }
}
