package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertProfileUpdateItemReview;
import com.ruoyi.expert.domain.ExpertProfileUpdateReviewRequest;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestion;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestionItem;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertProfileUpdateMapper;

@ExtendWith(MockitoExtension.class)
class ExpertProfileUpdateServiceTest
{
    @Mock
    private ExpertProfileUpdateMapper updateMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertOpenApiMapper openApiMapper;

    @Mock
    private ExpertDimensionViewService dimensionViewService;

    @Mock
    private KnowledgeGraphQueryService graphQueryService;

    @Mock
    private ExpertProfileEvidenceAgentService evidenceAgentService;

    @InjectMocks
    private ExpertProfileUpdateService profileUpdateService;

    @Test
    void generatesEvidenceBasedSuggestionWithoutChangingProfile()
    {
        ExpertServiceEvidence evidence = new ExpertServiceEvidence();
        evidence.setEvidenceId(5L);
        evidence.setUserId(12L);
        evidence.setEvidenceType("SERVICE_REPORT");
        evidence.setSummary("服务按期完成，报告完整，质量优秀");
        evidence.setSourceSystem("mock-dispatch");
        when(updateMapper.selectByEvidenceId(5L)).thenReturn(null);
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile());
        when(openApiMapper.selectRecentServiceEvidenceByUserId(12L, 10))
                .thenReturn(List.of(evidence));
        when(evidenceAgentService.analyze(any(), any(), any(), any())).thenReturn(null);
        when(evidenceAgentService.getModelName()).thenReturn("deepseek-chat");
        when(dimensionViewService.dimensions(12L, "DRAFT")).thenReturn(List.of(
                dimension("PERF_REPORT_QUALITY", "历史报告质量", 80),
                dimension("PERF_SIMILAR_PROJECT", "同类项目经验", 70)));
        doAnswer(invocation -> {
            ExpertProfileUpdateSuggestion suggestion = invocation.getArgument(0);
            suggestion.setSuggestionId(10L);
            return 1;
        }).when(updateMapper).insertSuggestion(any(ExpertProfileUpdateSuggestion.class));

        ExpertProfileUpdateSuggestion suggestion =
                profileUpdateService.generate(evidence);

        assertEquals(10L, suggestion.getSuggestionId());
        assertEquals(2, suggestion.getItems().size());
        assertEquals(83.0, suggestion.getItems().get(0).getSuggestedScore());
        assertEquals("RULE_FALLBACK", suggestion.getGenerationMode());
    }

    @Test
    void usesLiveAgentAnalysisWhenConfigured()
    {
        ExpertServiceEvidence evidence = new ExpertServiceEvidence();
        evidence.setEvidenceId(6L);
        evidence.setUserId(12L);
        evidence.setEvidenceType("SERVICE_REPORT");
        evidence.setSummary("报告按期提交，技术结论完整");
        when(updateMapper.selectByEvidenceId(6L)).thenReturn(null);
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile());
        when(openApiMapper.selectRecentServiceEvidenceByUserId(12L, 10))
                .thenReturn(List.of(evidence));
        when(dimensionViewService.dimensions(12L, "DRAFT")).thenReturn(List.of(
                dimension("PERF_REPORT_QUALITY", "历史报告质量", 80)));
        when(evidenceAgentService.analyze(any(), any(), any(), any())).thenReturn(
                new ExpertProfileEvidenceAgentService.Analysis(
                        "服务报告证据支持上调报告质量", 0.86,
                        List.of(new ExpertProfileEvidenceAgentService.Item(
                                "PERF_REPORT_QUALITY", 86, "报告按期且结论完整",
                                "报告按期提交，技术结论完整", 0.88)),
                        "{\"items\":[]}"));
        when(evidenceAgentService.getModelName()).thenReturn("deepseek-chat");
        doAnswer(invocation -> {
            ExpertProfileUpdateSuggestion suggestion = invocation.getArgument(0);
            suggestion.setSuggestionId(11L);
            return 1;
        }).when(updateMapper).insertSuggestion(any(ExpertProfileUpdateSuggestion.class));

        ExpertProfileUpdateSuggestion suggestion =
                profileUpdateService.generate(evidence);

        assertEquals("AGENT", suggestion.getGenerationMode());
        assertEquals("deepseek-chat", suggestion.getModelName());
        assertEquals(86.0, suggestion.getItems().get(0).getSuggestedScore());
    }

    @Test
    void approvingSuggestionCreatesNewProfileRelease()
    {
        ExpertProfileUpdateSuggestion suggestion = pendingSuggestion();
        ExpertProfileUpdateSuggestionItem item = suggestion.getItems().get(0);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setGraphVersion(2);
        Map<String, Object> latest = Map.of(
                "releaseId", 9L,
                "versionNo", 3,
                "graphVersion", 2,
                "sourceMaterialVersionsJson", "[]",
                "graphSnapshotJson", "{}",
                "profileSnapshotJson", "{\"dimensions\":["
                        + "{\"dimensionCode\":\"PERF_REPORT_QUALITY\","
                        + "\"dimensionName\":\"历史报告质量\",\"score\":80,"
                        + "\"dataStatus\":\"AVAILABLE\"}]}");
        when(updateMapper.selectByIdForUpdate(10L)).thenReturn(suggestion);
        when(updateMapper.selectItems(10L)).thenReturn(List.of(item));
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectLatestExpertReleaseVersion(12L)).thenReturn(latest);
        when(updateMapper.resolveSuggestion(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(updateMapper.selectById(10L)).thenReturn(suggestion);

        ExpertProfileUpdateReviewRequest request = new ExpertProfileUpdateReviewRequest();
        request.setAction("APPROVE");
        request.setVersion(0);
        LoginUser reviewer = new LoginUser();
        reviewer.setUserId(1L);
        SysUser user = new SysUser();
        user.setUserName("admin");
        reviewer.setUser(user);

        profileUpdateService.review(10L, request, reviewer);

        verify(platformMapper).insertExpertReleaseVersion(any());
        verify(updateMapper).updateProfileGraphVersion(12L, 3);
        verify(openApiMapper).updateServiceEvidenceStatus(5L, "APPLIED");
    }

    @Test
    void returnsApprovedReviewsForCurrentExpert()
    {
        ExpertProfileUpdateSuggestion suggestion = pendingSuggestion();
        suggestion.setStatus("APPROVED");
        ExpertProfileUpdateSuggestionItem item = suggestion.getItems().get(0);
        when(updateMapper.selectApprovedByUserId(12L, 20))
                .thenReturn(List.of(suggestion));
        when(updateMapper.selectItems(10L)).thenReturn(List.of(item));

        List<Map<String, Object>> reviews =
                profileUpdateService.approvedForExpert(12L);

        assertEquals(1, reviews.size());
        assertEquals(10L, reviews.get(0).get("suggestionId"));
        assertEquals(1, ((List<?>) reviews.get(0).get("items")).size());
        verify(updateMapper).selectApprovedByUserId(12L, 20);
    }

    @Test
    void savesEditedAiOpinionsWithoutApprovingSuggestion()
    {
        ExpertProfileUpdateSuggestion suggestion = pendingSuggestion();
        ExpertProfileUpdateSuggestionItem item = suggestion.getItems().get(0);
        when(updateMapper.selectByIdForUpdate(10L)).thenReturn(suggestion);
        when(updateMapper.selectItems(10L)).thenReturn(List.of(item));
        when(updateMapper.touchPendingSuggestion(10L, 0)).thenReturn(1);
        when(updateMapper.selectById(10L)).thenReturn(suggestion);

        ExpertProfileUpdateItemReview itemReview = new ExpertProfileUpdateItemReview();
        itemReview.setItemId(20L);
        itemReview.setApprovedScore(81.0);
        itemReview.setReviewedReason("审核人根据原报告重新核定的理由");
        ExpertProfileUpdateReviewRequest request = new ExpertProfileUpdateReviewRequest();
        request.setAction("SAVE");
        request.setVersion(0);
        request.setReviewedSummary("审核人修订后的画像摘要");
        request.setItems(List.of(itemReview));

        profileUpdateService.review(10L, request, reviewer());

        verify(updateMapper).updateReviewedSummary(10L, "审核人修订后的画像摘要");
        verify(updateMapper).updateItemReviewedReason(20L, "审核人根据原报告重新核定的理由");
        verify(updateMapper).updateItemApprovedScore(20L, 81.0);
        verify(updateMapper).touchPendingSuggestion(10L, 0);
        verify(updateMapper, never()).resolveSuggestion(
                any(), any(), any(), any(), any(), any(), any());
    }

    private static ExpertGraphDimension dimension(String code, String name, double score)
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode(code);
        dimension.setDimensionName(name);
        dimension.setScore(BigDecimal.valueOf(score));
        dimension.setDataStatus("AVAILABLE");
        return dimension;
    }

    private static ExpertProfile profile()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setRealName("测试专家");
        return profile;
    }

    private static ExpertProfileUpdateSuggestion pendingSuggestion()
    {
        ExpertProfileUpdateSuggestion suggestion = new ExpertProfileUpdateSuggestion();
        suggestion.setSuggestionId(10L);
        suggestion.setEvidenceId(5L);
        suggestion.setUserId(12L);
        suggestion.setStatus("PENDING");
        suggestion.setVersion(0);
        suggestion.setSummary("服务报告良好");
        ExpertProfileUpdateSuggestionItem item = new ExpertProfileUpdateSuggestionItem();
        item.setItemId(20L);
        item.setSuggestionId(10L);
        item.setDimensionCode("PERF_REPORT_QUALITY");
        item.setDimensionName("历史报告质量");
        item.setBeforeScore(80.0);
        item.setSuggestedScore(83.0);
        suggestion.setItems(List.of(item));
        return suggestion;
    }

    private static LoginUser reviewer()
    {
        LoginUser reviewer = new LoginUser();
        reviewer.setUserId(1L);
        SysUser user = new SysUser();
        user.setUserName("admin");
        reviewer.setUser(user);
        return reviewer;
    }
}
