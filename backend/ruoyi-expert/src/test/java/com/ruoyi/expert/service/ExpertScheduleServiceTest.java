package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertScheduleServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertCertificationService certificationService;

    @InjectMocks
    private ExpertWorkspaceService workspaceService;

    @Test
    void replacesWeeklyScheduleAndRefreshesCompletion()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(8L);
        profile.setRealName("测试专家");
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(2);
        schedule.setStartTime("09:30");
        schedule.setEndTime("12:00");
        schedule.setServiceMode("BOTH");
        schedule.setWeeklyCapacity(3);
        schedule.setEffectiveFrom("2026-11-17");
        when(platformMapper.selectProfileByUserIdForUpdate(8L)).thenReturn(profile);
        when(certificationService.profileStatusAfterReview(8L)).thenReturn("PENDING_REVIEW");
        when(platformMapper.selectProfileByUserId(8L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(8L)).thenReturn(List.of(schedule));

        workspaceService.saveSchedules(List.of(schedule), 8L, "operator");

        assertEquals(8L, schedule.getUserId());
        assertEquals("operator", schedule.getCreateBy());
        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).deleteScheduleByUserId(8L);
        order.verify(platformMapper).insertSchedule(schedule);
        order.verify(platformMapper).updateProfileReviewStatus(8L, "PENDING_REVIEW");
        order.verify(platformMapper).updateProfileCompletion(
                org.mockito.ArgumentMatchers.eq(8L), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void createsFutureTemporaryUnavailablePeriod()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(9L);
        ExpertUnavailablePeriod period = new ExpertUnavailablePeriod();
        period.setStartTime("2027-01-10T09:00");
        period.setEndTime("2027-01-10 12:00:00");
        period.setReason("异地评审");
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(platformMapper.selectUnavailablePeriodsByUserId(9L)).thenReturn(List.of());
        doAnswer(invocation -> {
            ExpertUnavailablePeriod inserted = invocation.getArgument(0);
            inserted.setUnavailableId(44L);
            return 1;
        }).when(platformMapper).insertUnavailablePeriod(period);
        when(platformMapper.selectUnavailablePeriodById(44L)).thenReturn(period);

        ExpertUnavailablePeriod saved =
                workspaceService.saveUnavailablePeriod(period, 9L, "expert");

        assertEquals("2027-01-10 09:00:00", saved.getStartTime());
        assertEquals("2027-01-10 12:00:00", saved.getEndTime());
        assertEquals(9L, saved.getUserId());
        assertEquals("expert", saved.getCreateBy());
    }
}
