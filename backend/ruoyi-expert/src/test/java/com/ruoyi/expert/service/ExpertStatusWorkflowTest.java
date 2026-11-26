package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.domain.ExpertStatusRequest;
import com.ruoyi.expert.domain.ExpertStatusReviewAction;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertStatusWorkflowTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertCertificationService certificationService;

    @Mock
    private KnowledgeGraphProjectionService graphProjectionService;

    @Mock
    private KnowledgeGraphOutboxService graphOutboxService;

    @Mock
    private ExpertCreditService creditService;

    @Mock
    private LoginUser reviewer;

    @InjectMocks
    private ExpertWorkspaceService workspaceService;

    @InjectMocks
    private ExpertReviewService reviewService;

    @Test
    void expertCanPauseImmediatelyWithoutReview()
    {
        ExpertProfile profile = profile(9L, "AVAILABLE");
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(certificationService.isCertified(9L)).thenReturn(true);
        when(platformMapper.selectPendingStatusRequestByUserId(9L)).thenReturn(null);

        ExpertStatusChange change = new ExpertStatusChange();
        change.setServiceStatus("PAUSED");
        change.setReason("  项目冲突，申请暂停  ");
        ExpertStatusRequest result = workspaceService.submitStatusChange(change, 9L, "expert-a");

        assertEquals(null, result);
        verify(platformMapper).updateProfileServiceStatus(9L, "PAUSED");
        verify(platformMapper).insertStatusLog(9L, "AVAILABLE", "PAUSED",
                "项目冲突，申请暂停", "0", 9L, "expert-a");
        verify(platformMapper).syncApprovedProfile(profile);
        verify(platformMapper, never()).insertStatusRequest(any());
        verify(platformMapper, never()).updateProfileServiceStatusIfCurrent(any(), any(), any());
    }

    @Test
    void expertExitCreatesReviewRequestWithoutChangingEffectiveStatus()
    {
        ExpertProfile profile = profile(9L, "AVAILABLE");
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(certificationService.isCertified(9L)).thenReturn(true);
        when(platformMapper.selectPendingStatusRequestByUserId(9L)).thenReturn(null);
        when(platformMapper.insertStatusRequest(any(ExpertStatusRequest.class))).thenAnswer(invocation -> {
            ExpertStatusRequest request = invocation.getArgument(0);
            request.setRequestId(31L);
            return 1;
        });
        ExpertStatusRequest saved = request(31L, 9L, "PENDING");
        when(platformMapper.selectStatusRequestById(31L)).thenReturn(saved);
        ExpertStatusChange change = new ExpertStatusChange();
        change.setServiceStatus("EXITED");
        change.setReason("退出专家库");

        ExpertStatusRequest result = workspaceService.submitStatusChange(change, 9L, "expert-a");

        assertEquals(31L, result.getRequestId());
        ArgumentCaptor<ExpertStatusRequest> requestCaptor =
                ArgumentCaptor.forClass(ExpertStatusRequest.class);
        verify(platformMapper).insertStatusRequest(requestCaptor.capture());
        assertEquals("AVAILABLE", requestCaptor.getValue().getSourceStatus());
        assertEquals("EXITED", requestCaptor.getValue().getTargetStatus());
        verify(platformMapper, never()).updateProfileServiceStatus(any(), any());
    }

    @Test
    void creditBlockedExpertCreatesRestoreRequest()
    {
        ExpertProfile profile = profile(9L, "CREDIT_BLOCKED");
        profile.setCreditScore(50);
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(certificationService.isCertified(9L)).thenReturn(true);
        when(platformMapper.selectPendingStatusRequestByUserId(9L)).thenReturn(null);
        when(platformMapper.insertStatusRequest(any(ExpertStatusRequest.class))).thenAnswer(invocation -> {
            ExpertStatusRequest request = invocation.getArgument(0);
            request.setRequestId(32L);
            return 1;
        });
        when(platformMapper.selectStatusRequestById(32L))
                .thenReturn(request(32L, 9L, "PENDING"));
        ExpertStatusChange change = new ExpertStatusChange();
        change.setServiceStatus("AVAILABLE");
        change.setReason("已整改并申请恢复接单");

        ExpertStatusRequest result = workspaceService.submitStatusChange(change, 9L, "expert-a");

        assertEquals(32L, result.getRequestId());
        ArgumentCaptor<ExpertStatusRequest> requestCaptor =
                ArgumentCaptor.forClass(ExpertStatusRequest.class);
        verify(platformMapper).insertStatusRequest(requestCaptor.capture());
        assertEquals("CREDIT_BLOCKED", requestCaptor.getValue().getSourceStatus());
        assertEquals("AVAILABLE", requestCaptor.getValue().getTargetStatus());
        assertEquals("1", requestCaptor.getValue().getCreditRelated());
        verify(platformMapper, never()).updateProfileServiceStatus(any(), any());
    }

    @Test
    void expertCannotCreateSecondPendingExitRequest()
    {
        when(platformMapper.selectProfileByUserIdForUpdate(9L))
                .thenReturn(profile(9L, "AVAILABLE"));
        when(certificationService.isCertified(9L)).thenReturn(true);
        when(platformMapper.selectPendingStatusRequestByUserId(9L))
                .thenReturn(request(30L, 9L, "PENDING"));
        ExpertStatusChange change = new ExpertStatusChange();
        change.setServiceStatus("EXITED");
        change.setReason("再次申请退出");

        assertThrows(ServiceException.class,
                () -> workspaceService.submitStatusChange(change, 9L, "expert-a"));
        verify(platformMapper, never()).insertStatusRequest(any());
    }

    @Test
    void switchingNormalStatusCancelsPendingExitRequest()
    {
        ExpertProfile profile = profile(10L, "AVAILABLE");
        ExpertStatusRequest pending = request(33L, 10L, "PENDING");
        when(platformMapper.selectProfileByUserIdForUpdate(10L)).thenReturn(profile);
        when(certificationService.isCertified(10L)).thenReturn(true);
        when(platformMapper.selectPendingStatusRequestByUserId(10L)).thenReturn(pending);
        when(platformMapper.cancelStatusRequest(33L, 10L, 0, "expert-c",
                "专家切换接单状态，待审申请自动撤销")).thenReturn(1);
        ExpertStatusChange change = new ExpertStatusChange();
        change.setServiceStatus("PAUSED");
        change.setReason("重新暂停接单");

        workspaceService.submitStatusChange(change, 10L, "expert-c");

        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).cancelStatusRequest(33L, 10L, 0, "expert-c",
                "专家切换接单状态，待审申请自动撤销");
        order.verify(platformMapper).updateProfileServiceStatus(10L, "PAUSED");
    }

    @Test
    void expertCancellationUsesProfileLockAndConditionalRequestUpdate()
    {
        ExpertStatusRequest overview = request(41L, 12L, "PENDING");
        ExpertStatusRequest locked = request(41L, 12L, "PENDING");
        when(platformMapper.selectStatusRequestById(41L)).thenReturn(overview, overview);
        when(platformMapper.selectProfileByUserIdForUpdate(12L))
                .thenReturn(profile(12L, "AVAILABLE"));
        when(platformMapper.selectStatusRequestByIdForUpdate(41L)).thenReturn(locked);
        when(platformMapper.cancelStatusRequest(41L, 12L, 0, "expert-b", "专家撤销状态申请"))
                .thenReturn(1);

        workspaceService.cancelStatusRequest(41L, 0, 12L, "expert-b");

        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).selectProfileByUserIdForUpdate(12L);
        order.verify(platformMapper).selectStatusRequestByIdForUpdate(41L);
        order.verify(platformMapper).cancelStatusRequest(41L, 12L, 0, "expert-b",
                "专家撤销状态申请");
    }

    @Test
    void reviewerApprovalLocksProfileBeforeResolvingRequest()
    {
        when(platformMapper.selectStatusRequestById(51L)).thenReturn(request(51L, 16L, "PENDING"));
        when(platformMapper.selectProfileByUserIdForUpdate(16L))
                .thenReturn(profile(16L, "AVAILABLE"));
        when(platformMapper.selectStatusRequestByIdForUpdate(51L))
                .thenReturn(request(51L, 16L, "PENDING"));
        when(platformMapper.updateProfileServiceStatusIfCurrent(16L, "AVAILABLE", "EXITED"))
                .thenReturn(1);
        when(platformMapper.resolveStatusRequest(51L, 0, "APPROVED", 7L, "reviewer-a", "同意"))
                .thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");
        ExpertStatusReviewAction action = new ExpertStatusReviewAction();
        action.setAction("APPROVE");
        action.setComment("同意");
        action.setVersion(0);

        reviewService.reviewStatusRequest(51L, action, reviewer);

        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).selectProfileByUserIdForUpdate(16L);
        order.verify(platformMapper).selectStatusRequestByIdForUpdate(51L);
        order.verify(platformMapper).updateProfileServiceStatusIfCurrent(16L, "AVAILABLE", "EXITED");
        order.verify(platformMapper).resolveStatusRequest(51L, 0, "APPROVED", 7L,
                "reviewer-a", "同意");
    }

    @Test
    void approvingCreditRestoreUpdatesStatusAndRestoresScore()
    {
        ExpertStatusRequest overview = request(52L, 17L, "PENDING");
        overview.setSourceStatus("CREDIT_BLOCKED");
        overview.setTargetStatus("AVAILABLE");
        ExpertStatusRequest locked = request(52L, 17L, "PENDING");
        locked.setSourceStatus("CREDIT_BLOCKED");
        locked.setTargetStatus("AVAILABLE");
        when(platformMapper.selectStatusRequestById(52L)).thenReturn(overview);
        when(platformMapper.selectProfileByUserIdForUpdate(17L))
                .thenReturn(profile(17L, "CREDIT_BLOCKED"));
        when(platformMapper.selectStatusRequestByIdForUpdate(52L)).thenReturn(locked);
        when(platformMapper.updateProfileServiceStatusIfCurrent(17L,
                "CREDIT_BLOCKED", "AVAILABLE")).thenReturn(1);
        when(platformMapper.resolveStatusRequest(52L, 0, "APPROVED", 7L,
                "reviewer-a", "整改完成")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(7L);
        when(reviewer.getUsername()).thenReturn("reviewer-a");
        ExpertStatusReviewAction action = new ExpertStatusReviewAction();
        action.setAction("APPROVE");
        action.setComment("整改完成");
        action.setVersion(0);

        reviewService.reviewStatusRequest(52L, action, reviewer);

        verify(creditService).restoreAfterApprovedRequest(17L, 52L, reviewer);
    }

    @Test
    void secondReviewerCannotProcessAnAlreadyResolvedRequest()
    {
        when(platformMapper.selectStatusRequestById(61L)).thenReturn(request(61L, 18L, "PENDING"));
        when(platformMapper.selectProfileByUserIdForUpdate(18L))
                .thenReturn(profile(18L, "PAUSED"));
        when(platformMapper.selectStatusRequestByIdForUpdate(61L))
                .thenReturn(request(61L, 18L, "APPROVED"));
        ExpertStatusReviewAction action = new ExpertStatusReviewAction();
        action.setAction("APPROVE");
        action.setVersion(0);

        assertThrows(ServiceException.class,
                () -> reviewService.reviewStatusRequest(61L, action, reviewer));
        verify(platformMapper, never()).updateProfileServiceStatusIfCurrent(any(), any(), any());
    }

    private static ExpertProfile profile(Long userId, String status)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setServiceStatus(status);
        profile.setReviewStatus("APPROVED");
        profile.setVersion(0);
        return profile;
    }

    private static ExpertStatusRequest request(Long requestId, Long userId, String status)
    {
        ExpertStatusRequest request = new ExpertStatusRequest();
        request.setRequestId(requestId);
        request.setUserId(userId);
        request.setSourceStatus("AVAILABLE");
        request.setTargetStatus("EXITED");
        request.setRequestStatus(status);
        request.setReason("申请人申请退出专家库");
        request.setCreditRelated("0");
        request.setVersion(0);
        return request;
    }
}
