package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertRiskReview;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertCreditServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private LoginUser reviewer;

    @InjectMocks
    private ExpertCreditService creditService;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(creditService, "blockThreshold", 60);
        ReflectionTestUtils.setField(creditService, "restoreScore", 60);
        ReflectionTestUtils.setField(creditService, "lowRiskDeduction", 2);
        ReflectionTestUtils.setField(creditService, "mediumRiskDeduction", 5);
        ReflectionTestUtils.setField(creditService, "highRiskDeduction", 10);
    }

    @Test
    void mediumRiskDeductionBlocksExpertBelowThreshold()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        profile.setCreditScore(62);
        ExpertRiskReview risk = new ExpertRiskReview();
        risk.setRiskId(30L);
        risk.setUserId(12L);
        risk.setSeverity("MEDIUM");
        risk.setTitle("服务低评价");
        when(platformMapper.selectProfileByUserIdForUpdate(12L)).thenReturn(profile);
        when(platformMapper.updateProfileServiceStatusIfCurrent(12L, "AVAILABLE",
                "CREDIT_BLOCKED")).thenReturn(1);
        when(reviewer.getUserId()).thenReturn(1L);
        when(reviewer.getUsername()).thenReturn("admin");

        Map<String, Object> change = creditService.deductForRisk(risk, reviewer);

        assertEquals(57, change.get("afterScore"));
        assertEquals(5, change.get("deduction"));
        assertEquals(true, change.get("blocked"));
        verify(platformMapper).updateCreditScore(12L, 57);
        verify(platformMapper).insertCreditLog(12L, "RISK_DEDUCTION", 30L, null,
                62, 57, -5, "风险预警审核确认：服务低评价", 1L, "admin");
        verify(platformMapper).insertStatusLog(12L, "AVAILABLE", "CREDIT_BLOCKED",
                "风险预警确认后信誉分降至 57，低于阈值 60，系统自动禁止接单",
                "1", 1L, "admin");
        verify(platformMapper).cancelPendingStatusRequests(12L, "admin",
                "信誉分低于阈值自动禁止接单，待审状态申请自动撤销");
        verify(platformMapper).syncApprovedProfile(profile);
    }

    @Test
    void approvedRestoreApplicationRaisesScoreToThreshold()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setCreditScore(50);
        when(platformMapper.selectProfileByUserIdForUpdate(12L)).thenReturn(profile);
        when(reviewer.getUserId()).thenReturn(1L);
        when(reviewer.getUsername()).thenReturn("admin");

        Map<String, Object> change = creditService.restoreAfterApprovedRequest(
                12L, 40L, reviewer);

        assertEquals(60, change.get("afterScore"));
        assertTrue((Integer) change.get("afterScore") >= 60);
        verify(platformMapper).updateCreditScore(12L, 60);
        verify(platformMapper).insertCreditLog(12L, "RESTORE_APPROVED", null, 40L,
                50, 60, 10, "信誉恢复申请审核通过", 1L, "admin");
    }
}
