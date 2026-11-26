package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmAction;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmation;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertTaskConflictMapper;

@ExtendWith(MockitoExtension.class)
class ExpertTaskConflictServiceTest
{
    @Mock
    private ExpertTaskConflictMapper taskConflictMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @InjectMocks
    private ExpertTaskConflictService taskConflictService;

    @Test
    void createsPendingConfirmationRequest()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        ExpertTaskConflictConfirmation request = request();
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L)).thenReturn(List.of());
        when(taskConflictMapper.selectByTaskAndExpert(
                request.getExternalTaskId(), 12L,
                "2026-11-24 09:00:00", "2026-11-24 12:00:00"))
                .thenReturn(null);
        doAnswer(invocation -> {
            ExpertTaskConflictConfirmation item = invocation.getArgument(0);
            item.setConfirmationId(30L);
            return 1;
        }).when(taskConflictMapper).insertConfirmation(any());
        when(taskConflictMapper.selectById(30L)).thenReturn(request);

        ExpertTaskConflictConfirmation saved =
                taskConflictService.request(request, "operator");

        assertEquals(30L, saved.getConfirmationId());
        assertEquals("operator", saved.getRequestedBy());
    }

    @Test
    void rejectsRequestOverlappingTemporaryUnavailablePeriod()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        ExpertUnavailablePeriod unavailable = new ExpertUnavailablePeriod();
        unavailable.setStartTime("2026-11-24 08:00:00");
        unavailable.setEndTime("2026-11-24 10:00:00");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L))
                .thenReturn(List.of(schedule("09:00", "12:00")));
        when(platformMapper.selectUnavailablePeriodsByUserId(12L))
                .thenReturn(List.of(unavailable));

        assertThrows(com.ruoyi.common.exception.ServiceException.class,
                () -> taskConflictService.request(request(), "operator"));
    }

    @Test
    void resolvesPendingTaskConflictAsPotential()
    {
        ExpertTaskConflictConfirmation pending = request();
        pending.setStatus("PENDING");
        pending.setConflictStatus("PENDING");
        when(taskConflictMapper.selectLatestByTask(
                "TASK-001", 12L,
                "2026-11-24 09:00:00", "2026-11-24 12:00:00"))
                .thenReturn(pending);

        ExpertTaskConflictService.TaskConflictResult result =
                taskConflictService.resolve("TASK-001", 12L,
                        LocalDateTime.of(2026, 11, 24, 9, 0),
                        LocalDateTime.of(2026, 11, 24, 12, 0));

        assertEquals("PENDING", result.confirmationStatus());
        assertEquals("PENDING", result.acceptanceStatus());
        assertEquals("POTENTIAL", result.conflictStatus());
    }

    @Test
    void acceptsTaskAndConfirmsNoConflict()
    {
        ExpertTaskConflictConfirmation pending = request();
        pending.setConfirmationId(30L);
        pending.setStatus("PENDING");
        pending.setVersion(0);
        ExpertTaskConflictConfirmation accepted = request();
        accepted.setConfirmationId(30L);
        accepted.setStatus("ACCEPTED");
        accepted.setConflictStatus("NONE");
        when(taskConflictMapper.selectByIdForUpdate(30L)).thenReturn(pending);
        when(taskConflictMapper.confirm(30L, 12L, "ACCEPTED", "NONE",
                "我已核对任务相关方，无利益冲突", 0)).thenReturn(1);
        when(taskConflictMapper.selectById(30L)).thenReturn(accepted);

        ExpertTaskConflictConfirmAction action = new ExpertTaskConflictConfirmAction();
        action.setAcceptanceStatus("ACCEPTED");
        action.setConflictStatus("NONE");
        action.setStatement("我已核对任务相关方，无利益冲突");
        action.setVersion(0);

        ExpertTaskConflictConfirmation result =
                taskConflictService.confirm(30L, 12L, action);

        assertEquals("ACCEPTED", result.getStatus());
        assertEquals("NONE", result.getConflictStatus());
    }

    @Test
    void blocksAcceptanceWhenConflictIsConfirmed()
    {
        ExpertTaskConflictConfirmation pending = request();
        pending.setConfirmationId(30L);
        pending.setStatus("PENDING");
        pending.setVersion(0);
        when(taskConflictMapper.selectByIdForUpdate(30L)).thenReturn(pending);

        ExpertTaskConflictConfirmAction action = new ExpertTaskConflictConfirmAction();
        action.setAcceptanceStatus("ACCEPTED");
        action.setConflictStatus("CONFIRMED");
        action.setStatement("本次任务存在明确冲突");
        action.setVersion(0);

        assertThrows(com.ruoyi.common.exception.ServiceException.class,
                () -> taskConflictService.confirm(30L, 12L, action));
    }

    @Test
    void confirmsFormalArrangementAndCreatesOccupiedSnapshot()
    {
        ExpertTaskConflictConfirmation accepted = request();
        accepted.setConfirmationId(30L);
        accepted.setStatus("ACCEPTED");
        accepted.setConflictStatus("NONE");
        accepted.setArrangementStatus("NOT_CONFIRMED");
        accepted.setVersion(1);
        ExpertTaskConflictConfirmation arranged = request();
        arranged.setConfirmationId(30L);
        arranged.setStatus("ACCEPTED");
        arranged.setConflictStatus("NONE");
        arranged.setArrangementStatus("CONFIRMED");
        arranged.setVersion(2);
        when(taskConflictMapper.selectByIdForUpdate(30L)).thenReturn(accepted);
        when(taskConflictMapper.updateArrangement(30L, "NOT_CONFIRMED", "CONFIRMED",
                "staff", "business-workspace", "arrangement-30", 1)).thenReturn(1);
        when(taskConflictMapper.selectById(30L)).thenReturn(arranged);

        ExpertTaskConflictConfirmation result =
                taskConflictService.confirmArrangement(30L, "staff", "business-workspace");

        assertEquals("CONFIRMED", result.getArrangementStatus());
        org.mockito.Mockito.verify(taskConflictMapper).upsertOccupancySnapshot(
                12L, "TASK-001", "2026-11-24 09:00:00",
                "2026-11-24 12:00:00", "REMOTE", null, null,
                "OCCUPIED", "NONE", "business-workspace",
                "EXTERNAL", "arrangement-30", "staff");
    }

    private static ExpertTaskConflictConfirmation request()
    {
        ExpertTaskConflictConfirmation request = new ExpertTaskConflictConfirmation();
        request.setExternalTaskId("TASK-001");
        request.setUserId(12L);
        request.setServiceStart("2026-11-24 09:00:00");
        request.setServiceEnd("2026-11-24 12:00:00");
        request.setServiceMode("REMOTE");
        return request;
    }

    private static ExpertSchedule schedule(String start, String end)
    {
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(2);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setServiceMode("REMOTE");
        schedule.setWeeklyCapacity(1);
        schedule.setEffectiveFrom("2026-11-01");
        return schedule;
    }
}
