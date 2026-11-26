package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertRiskReview;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertRiskReviewMapper;

@ExtendWith(MockitoExtension.class)
class ExpertRiskReviewServiceTest
{
    @Mock
    private ExpertRiskReviewMapper riskMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @InjectMocks
    private ExpertRiskReviewService riskReviewService;

    @Test
    void createsRiskReviewFromNegativeEvaluationEvidence()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        ExpertServiceEvidence evidence = new ExpertServiceEvidence();
        evidence.setEvidenceId(7L);
        evidence.setSourceEventId("EVENT-RISK-1");
        evidence.setUserId(12L);
        evidence.setEvidenceType("EVALUATION");
        evidence.setSourceSystem("business");
        evidence.setSourceKind("EXTERNAL");
        evidence.setOccurredAt("2026-11-19 10:00:00");
        evidence.setTitle("服务评价");
        evidence.setSummary("需求方不满意，并正式投诉服务逾期");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(riskMapper.selectBySource("EVENT-RISK-1", "LOW_EVALUATION"))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExpertRiskReview risk = invocation.getArgument(0);
            risk.setRiskId(20L);
            return 1;
        }).when(riskMapper).insertRisk(any(ExpertRiskReview.class));

        ExpertRiskReview risk = riskReviewService.createFromEvidence(evidence);

        assertEquals(20L, risk.getRiskId());
        assertEquals("LOW_EVALUATION", risk.getRiskType());
        assertEquals("OPEN", risk.getStatus());
    }

    @Test
    void returnsAcknowledgedRiskAlertsForCurrentExpert()
    {
        ExpertRiskReview risk = new ExpertRiskReview();
        risk.setRiskId(20L);
        risk.setUserId(12L);
        risk.setStatus("ACKNOWLEDGED");
        when(riskMapper.selectAcknowledgedByUserId(12L, 20))
                .thenReturn(List.of(risk));

        List<Map<String, Object>> alerts =
                riskReviewService.acknowledgedForExpert(12L);

        assertEquals(1, alerts.size());
        assertEquals(20L, alerts.get(0).get("riskId"));
        assertEquals("ACKNOWLEDGED", risk.getStatus());
        verify(riskMapper).selectAcknowledgedByUserId(12L, 20);
    }
}
