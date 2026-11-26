package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertIntegrationSnapshotServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertBusinessMapper businessMapper;

    @Mock
    private ExpertDimensionViewService dimensionViewService;

    @InjectMocks
    private ExpertIntegrationSnapshotService snapshotService;

    @Test
    void returnsCompleteMatchingSnapshotWithoutMakingDispatchDecision()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setRealName("测试专家");
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        profile.setIndustryField("新能源");
        profile.setResearchField("机械工程");
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(1);
        schedule.setStartTime("09:00");
        schedule.setEndTime("12:00");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L)).thenReturn(List.of(schedule));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectLatestExpertReleaseVersion(12L)).thenReturn(null);
        when(dimensionViewService.dimensions(12L, "DRAFT")).thenReturn(List.of());
        when(businessMapper.selectLatestConflictDisclosure(12L)).thenReturn(null);

        Map<String, Object> snapshot = snapshotService.snapshot(12L);

        assertEquals(true, snapshot.get("availableForMatching"));
        assertEquals(false, snapshot.get("matchingDecision"));
        assertEquals(false, snapshot.get("dispatchDecision"));
        assertTrue(((List<?>) snapshot.get("schedules")).size() == 1);
        assertTrue(((List<?>) snapshot.get("capabilityTags")).size() >= 2);
    }

    @Test
    void batchDeduplicatesExpertIds()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(platformMapper.selectLatestExpertReleaseVersion(12L)).thenReturn(null);
        when(dimensionViewService.dimensions(12L, "DRAFT")).thenReturn(List.of());
        when(businessMapper.selectLatestConflictDisclosure(12L)).thenReturn(null);

        List<Map<String, Object>> snapshots = snapshotService.batch(List.of(12L, 12L));

        assertEquals(1, snapshots.size());
        assertFalse((Boolean) snapshots.get(0).get("matchingDecision"));
    }
}
