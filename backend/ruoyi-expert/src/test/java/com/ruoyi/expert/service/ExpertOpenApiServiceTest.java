package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.domain.ExpertServiceEvidenceRequest;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertOpenApiServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertOpenApiMapper openApiMapper;

    @Mock
    private BusinessContextPort businessContextPort;

    @Mock
    private ExpertProfileUpdateService profileUpdateService;

    @Mock
    private ExpertRiskReviewService riskReviewService;

    @Mock
    private ExpertTaskConflictService taskConflictService;

    @InjectMocks
    private ExpertOpenApiService openApiService;

    @BeforeEach
    void setUpTaskConflict()
    {
        lenient().when(taskConflictService.resolve(any(), any(), any(), any()))
                .thenReturn(new ExpertTaskConflictService.TaskConflictResult(
                        "ACCEPTED", "ACCEPTED", "NONE", 1L));
    }

    @Test
    void returnsEligibleOnlyWhenScheduleAndBusinessContextAreVerified()
    {
        ExpertProfile profile = availableProfile(12L);
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectPendingStatusRequestByUserId(12L)).thenReturn(null);
        when(businessContextPort.inspect(any(BusinessContextPort.BusinessContextQuery.class)))
                .thenReturn(context("FREE", "NONE"));

        Map<String, Object> result = openApiService.checkEligibility(request(12L));

        assertEquals(ExpertOpenApiService.ELIGIBLE, result.get("decision"));
        assertFalse((Boolean) result.get("manualConfirmationRequired"));
        assertFalse((Boolean) result.get("candidateRanked"));
    }

    @Test
    void returnsManualConfirmationWhenBusinessContextIsUnknown()
    {
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(availableProfile(12L));
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectPendingStatusRequestByUserId(12L)).thenReturn(null);
        when(businessContextPort.inspect(any(BusinessContextPort.BusinessContextQuery.class)))
                .thenReturn(context("UNKNOWN", "UNKNOWN"));

        Map<String, Object> result = openApiService.checkEligibility(request(12L));

        assertEquals(ExpertOpenApiService.REQUIRES_MANUAL_CONFIRMATION,
                result.get("decision"));
        assertTrue((Boolean) result.get("manualConfirmationRequired"));
    }

    @Test
    void occupiedBusinessContextBlocksEligibility()
    {
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(availableProfile(12L));
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectPendingStatusRequestByUserId(12L)).thenReturn(null);
        when(businessContextPort.inspect(any(BusinessContextPort.BusinessContextQuery.class)))
                .thenReturn(context("OCCUPIED", "NONE"));

        Map<String, Object> result = openApiService.checkEligibility(request(12L));

        assertEquals(ExpertOpenApiService.INELIGIBLE, result.get("decision"));
    }

    @Test
    void temporaryUnavailableIntervalBlocksEligibility()
    {
        ExpertUnavailablePeriod unavailable = new ExpertUnavailablePeriod();
        unavailable.setStartTime("2027-01-13 09:30:00");
        unavailable.setEndTime("2027-01-13 10:15:00");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(availableProfile(12L));
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L))
                .thenReturn(List.of(unavailable));
        when(platformMapper.selectPendingStatusRequestByUserId(12L)).thenReturn(null);
        when(businessContextPort.inspect(any(BusinessContextPort.BusinessContextQuery.class)))
                .thenReturn(context("FREE", "NONE"));

        Map<String, Object> result = openApiService.checkEligibility(request(12L));

        assertEquals(ExpertOpenApiService.INELIGIBLE, result.get("decision"));
    }

    @Test
    void receivesServiceEvidenceWithoutUpdatingProfile()
    {
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(availableProfile(12L));
        when(openApiMapper.selectServiceEvidenceBySourceEventId("EVENT-001"))
                .thenReturn(null);
        when(profileUpdateService.generate(any(ExpertServiceEvidence.class)))
                .thenReturn(null);
        when(riskReviewService.createFromEvidence(any(ExpertServiceEvidence.class)))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExpertServiceEvidence evidence = invocation.getArgument(0);
            evidence.setEvidenceId(55L);
            return 1;
        }).when(openApiMapper).insertServiceEvidence(any(ExpertServiceEvidence.class));

        ExpertServiceEvidenceRequest request = new ExpertServiceEvidenceRequest();
        request.setSourceEventId("EVENT-001");
        request.setUserId(12L);
        request.setEvidenceType("SERVICE_REPORT");
        request.setSourceSystem("mock-dispatch");
        request.setSourceKind("MOCK");
        request.setRawPayload("{\"quality\":\"GOOD\"}");

        Map<String, Object> result =
                openApiService.ingestServiceEvidence(request, "tester");

        assertEquals(55L, result.get("evidenceId"));
        assertEquals("RECEIVED", result.get("processingStatus"));
        assertFalse((Boolean) result.get("profileUpdated"));
        verify(openApiMapper).insertServiceEvidence(any(ExpertServiceEvidence.class));
    }

    private static ExpertEligibilityRequest request(Long userId)
    {
        ExpertEligibilityRequest request = new ExpertEligibilityRequest();
        request.setUserId(userId);
        request.setExternalTaskId("TASK-001");
        request.setServiceStart("2027-01-13T09:00:00");
        request.setServiceEnd("2027-01-13T12:00:00");
        request.setServiceMode("REMOTE");
        return request;
    }

    private static ExpertProfile availableProfile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setRealName("测试专家");
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        profile.setVersion(3);
        return profile;
    }

    private static ExpertSchedule schedule(String start, String end)
    {
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(3);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setServiceMode("BOTH");
        schedule.setWeeklyCapacity(2);
        schedule.setEffectiveFrom("2026-01-01");
        return schedule;
    }

    private static BusinessContextPort.BusinessContextResult context(
            String occupancyStatus, String conflictStatus)
    {
        return new BusinessContextPort.BusinessContextResult(
                occupancyStatus, conflictStatus, "MOCK,mock-dispatch",
                "2027-01-01 09:00:00", List.of("TASK-001"));
    }
}
