package com.ruoyi.expert.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertProfileDimensionAggregationServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

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
        when(platformMapper.selectDimensionAssessments(12L, false))
                .thenReturn(List.of(
                        assessment("A", 10L, 60, 0.8, 100L),
                        assessment("A", 11L, 90, 0.8, 101L),
                        assessment("B", 11L, 75, 0.7, 102L)));

        aggregationService.rebuildDraft(profile);

        verify(platformMapper).deleteDraftDimensionEvidence(12L);
        verify(platformMapper, times(3)).insertDimensionEvidence(anyMap());
        verify(platformMapper).deleteDraftGraph(12L);
        verify(platformMapper).insertGraphScore(eq(12L), eq("A"), eq(75.0), eq(0.8),
                anyString(), eq("DRAFT"), eq(1), eq("EVIDENCE_SCORE"), eq("1"),
                eq("DIMENSION_ASSESSMENT_AGGREGATE"), eq(2), isNull(), isNull(),
                isNull(), eq(0), eq("profile-assessment-v3"));
        verify(platformMapper).insertGraphScore(eq(12L), eq("B"), eq(75.0), eq(0.7),
                anyString(), eq("DRAFT"), eq(1), eq("EVIDENCE_SCORE"), eq("0"),
                eq("DIMENSION_ASSESSMENT_AGGREGATE"), eq(1), isNull(), isNull(),
                isNull(), eq(0), eq("profile-assessment-v3"));
    }

    @Test
    void acceptedOnlyRebuildPassesReviewFilterToGraphSource()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(15L);
        when(platformMapper.selectGraphDimensions(15L, "DRAFT")).thenReturn(List.of());
        when(platformMapper.selectDimensionAssessments(15L, true)).thenReturn(List.of());

        aggregationService.rebuildDraft(profile, true);

        verify(platformMapper).selectDimensionAssessments(15L, true);
    }

    private static ExpertGraphDimension dimension(String code)
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode(code);
        return dimension;
    }

    private static java.util.Map<String, Object> assessment(String code, Long materialId,
            double score, double confidence, Long assessmentId)
    {
        return java.util.Map.of(
                "dimensionCode", code,
                "materialId", materialId,
                "score", score,
                "confidence", confidence,
                "evidence", "图谱材料证据",
                "assessmentId", assessmentId,
                "fileName", "material-" + materialId + ".pdf");
    }
}
