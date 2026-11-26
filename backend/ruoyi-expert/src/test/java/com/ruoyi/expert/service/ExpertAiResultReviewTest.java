package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertAiResultEditRequest;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertReviewAction;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class ExpertAiResultReviewTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Mock
    private LoginUser reviewer;

    @InjectMocks
    private ExpertReviewService reviewService;

    @Test
    void reviewedValidityLastsThreeMonths()
    {
        assertEquals(LocalDate.of(2027, 2, 23),
                ExpertReviewService.reviewedValidityEnd(LocalDate.of(2026, 11, 23)));
    }

    @Test
    void reviewerCanAcceptOneFieldMapping()
    {
        ExpertMaterial overview = material(5L, 80L);
        ExpertMaterial locked = material(5L, 80L);
        when(platformMapper.selectMaterialById(80L)).thenReturn(overview);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(80L)).thenReturn(locked);
        when(platformMapper.updateFieldMappingReview(80L, 901L, "ACCEPTED", "",
                "reviewer-a")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");

        reviewService.reviewAiResult(80L, "FIELD", 901L, "ACCEPTED", reviewer);

        verify(platformMapper).updateFieldMappingReview(80L, 901L, "ACCEPTED", "",
                "reviewer-a");
        verify(platformMapper).insertReviewLog(5L, 80L, "AI_RESULT_ACCEPTED",
                7L, "reviewer-a", "PENDING", "ACCEPTED", "");
    }

    @Test
    void reviewerConfirmsRelationThroughMaterialEvidence()
    {
        ExpertMaterial overview = material(5L, 80L);
        ExpertMaterial locked = material(5L, 80L);
        when(platformMapper.selectMaterialById(80L)).thenReturn(overview);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(80L)).thenReturn(locked);
        when(graphMapper.updateMaterialRelationReview(80L, 902L, "REJECTED"))
                .thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");

        reviewService.reviewAiResult(80L, "RELATION", 902L, "REJECTED", reviewer);

        verify(graphMapper).updateMaterialRelationReview(80L, 902L, "REJECTED");
        verify(graphMapper).updateRelationReviewStatus(902L, "REJECTED");
    }

    @Test
    void reviewerEditsFieldMappingWithoutChangingReviewStatus()
    {
        ExpertMaterial overview = material(5L, 80L);
        ExpertMaterial locked = material(5L, 80L);
        when(platformMapper.selectMaterialById(80L)).thenReturn(overview);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(80L)).thenReturn(locked);
        when(platformMapper.updateFieldMappingOpinion(80L, 901L, "审核修订值", 0.96,
                "审核修订证据", "人工核对", "reviewer-a")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");
        ExpertAiResultEditRequest request = new ExpertAiResultEditRequest();
        request.setReviewedValue("审核修订值");
        request.setReviewedConfidence(0.96);
        request.setReviewedEvidence("审核修订证据");
        request.setReviewComment("人工核对");

        reviewService.editAiResult(80L, "FIELD", 901L, request, reviewer);

        verify(platformMapper).updateFieldMappingOpinion(80L, 901L, "审核修订值", 0.96,
                "审核修订证据", "人工核对", "reviewer-a");
        verify(platformMapper).insertReviewLog(5L, 80L, "AI_RESULT_EDITED",
                7L, "reviewer-a", "PENDING", "PENDING", "人工核对");
    }

    @Test
    void reviewerEditsDimensionScoreAndEvidence()
    {
        ExpertMaterial overview = material(5L, 80L);
        ExpertMaterial locked = material(5L, 80L);
        when(platformMapper.selectMaterialById(80L)).thenReturn(overview);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(80L)).thenReturn(locked);
        when(platformMapper.updateDimensionAssessmentOpinion(80L, 903L, 88.0,
                "审核修订维度证据", "人工复核", "reviewer-a")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");
        ExpertAiResultEditRequest request = new ExpertAiResultEditRequest();
        request.setReviewedScore(88.0);
        request.setReviewedEvidence("审核修订维度证据");
        request.setReviewComment("人工复核");

        reviewService.editAiResult(80L, "DIMENSION", 903L, request, reviewer);

        verify(platformMapper).updateDimensionAssessmentOpinion(80L, 903L, 88.0,
                "审核修订维度证据", "人工复核", "reviewer-a");
    }

    @Test
    void reviewerEditsRelationEvidence()
    {
        ExpertMaterial overview = material(5L, 80L);
        ExpertMaterial locked = material(5L, 80L);
        when(platformMapper.selectMaterialById(80L)).thenReturn(overview);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(80L)).thenReturn(locked);
        when(graphMapper.updateMaterialRelationOpinion(80L, 902L,
                "修正后的专家", "Expert", "EXPERTISE_IN",
                "修正后的能力", "Capability",
                "审核修订关系证据", "人工核对关系", "reviewer-a")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");
        ExpertAiResultEditRequest request = new ExpertAiResultEditRequest();
        request.setReviewedSourceName("修正后的专家");
        request.setReviewedSourceType("Expert");
        request.setReviewedRelationType("EXPERTISE_IN");
        request.setReviewedTargetName("修正后的能力");
        request.setReviewedTargetType("Capability");
        request.setReviewedExcerpt("审核修订关系证据");
        request.setReviewComment("人工核对关系");

        reviewService.editAiResult(80L, "RELATION", 902L, request, reviewer);

        verify(graphMapper).updateMaterialRelationOpinion(80L, 902L,
                "修正后的专家", "Expert", "EXPERTISE_IN",
                "修正后的能力", "Capability",
                "审核修订关系证据", "人工核对关系", "reviewer-a");
    }

    @Test
    void finalApprovalIsBlockedWhileAiResultsRemainPending()
    {
        ExpertMaterial material = material(5L, 81L);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        profile.setReviewStatus("PENDING_REVIEW");
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectMaterialById(81L)).thenReturn(material);
        when(platformMapper.selectMaterialList(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(material));
        when(platformMapper.selectFieldMappings(81L))
                .thenReturn(List.of(Map.of("reviewStatus", "PENDING")));
        when(platformMapper.selectMaterialDimensionAssessments(81L)).thenReturn(List.of());
        when(graphMapper.selectMaterialReviewFacts(81L)).thenReturn(List.of());
        ExpertReviewAction action = new ExpertReviewAction();
        action.setMaterialId(81L);
        action.setAction("PASS");

        assertThrows(ServiceException.class,
                () -> reviewService.action(5L, action, reviewer));
    }

    private static ExpertMaterial material(Long userId, Long materialId)
    {
        ExpertMaterial material = new ExpertMaterial();
        material.setUserId(userId);
        material.setMaterialId(materialId);
        material.setMaterialCategory("IDENTITY");
        material.setReviewStatus("PENDING");
        material.setJobStatus("REVIEW_PENDING");
        return material;
    }
}
