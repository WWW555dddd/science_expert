package com.ruoyi.expert.service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmAction;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmation;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertTaskConflictMapper;

/**
 * 任务级利益冲突确认。
 */
@Service
public class ExpertTaskConflictService
{
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> CONFLICT_STATUSES =
            Set.of("NONE", "POTENTIAL", "CONFIRMED");
    private static final Set<String> ACCEPTANCE_STATUSES =
            Set.of("ACCEPTED", "REJECTED");

    @Autowired
    private ExpertTaskConflictMapper taskConflictMapper;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Transactional
    public ExpertTaskConflictConfirmation request(
            ExpertTaskConflictConfirmation request, String requestedBy)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(request.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        if (!"APPROVED".equals(profile.getReviewStatus())
                || !"AVAILABLE".equals(profile.getServiceStatus()))
        {
            throw new ServiceException("专家当前没有有效接单资格，不能发起接单");
        }
        request.setServiceStart(normalizeDateTime(request.getServiceStart()));
        request.setServiceEnd(normalizeDateTime(request.getServiceEnd()));
        if (!LocalDateTime.parse(request.getServiceStart(), DATETIME_FORMAT)
                .isBefore(LocalDateTime.parse(request.getServiceEnd(), DATETIME_FORMAT)))
        {
            throw new ServiceException("服务结束时间必须晚于服务开始时间");
        }
        LocalDateTime serviceStart = LocalDateTime.parse(
                request.getServiceStart(), DATETIME_FORMAT);
        LocalDateTime serviceEnd = LocalDateTime.parse(
                request.getServiceEnd(), DATETIME_FORMAT);
        validateDeclaredAvailability(request.getUserId(), serviceStart,
                serviceEnd, upper(request.getServiceMode()));
        validateNoTemporaryUnavailable(request.getUserId(), serviceStart, serviceEnd);
        ExpertTaskConflictConfirmation existing =
                taskConflictMapper.selectByTaskAndExpert(
                        request.getExternalTaskId(), request.getUserId(),
                        request.getServiceStart(), request.getServiceEnd());
        if (existing != null)
        {
            return existing;
        }
        request.setRequestedBy(requestedBy);
        request.setSourceSystem(StringUtils.isEmpty(request.getSourceSystem())
                ? "topic2-matching" : request.getSourceSystem());
        taskConflictMapper.insertConfirmation(request);
        return taskConflictMapper.selectById(request.getConfirmationId());
    }

    @Transactional(readOnly = true)
    public List<ExpertTaskConflictConfirmation> listForExpert(Long userId, String status)
    {
        return taskConflictMapper.selectByExpert(userId,
                StringUtils.isEmpty(status) ? null : upper(status));
    }

    @Transactional
    public ExpertTaskConflictConfirmation confirm(Long confirmationId, Long userId,
            ExpertTaskConflictConfirmAction action)
    {
        String conflictStatus = upper(action.getConflictStatus());
        String acceptanceStatus = upper(action.getAcceptanceStatus());
        if (!ACCEPTANCE_STATUSES.contains(acceptanceStatus))
        {
            throw new ServiceException("接单结果仅支持 ACCEPTED 或 REJECTED");
        }
        if (!CONFLICT_STATUSES.contains(conflictStatus))
        {
            throw new ServiceException("冲突结果仅支持 NONE、POTENTIAL、CONFIRMED");
        }
        ExpertTaskConflictConfirmation confirmation =
                taskConflictMapper.selectByIdForUpdate(confirmationId);
        if (confirmation == null || !Objects.equals(confirmation.getUserId(), userId))
        {
            throw new ServiceException("专家接单请求不存在");
        }
        if (!"PENDING".equals(confirmation.getStatus()))
        {
            throw new ServiceException("该任务冲突已经确认");
        }
        if (StringUtils.isEmpty(confirmation.getExternalTaskId())
                || StringUtils.isEmpty(confirmation.getServiceStart())
                || StringUtils.isEmpty(confirmation.getServiceEnd()))
        {
            throw new ServiceException("接单任务信息不完整，请联系业务经办人员重新发起");
        }
        if (!Objects.equals(confirmation.getVersion(), action.getVersion()))
        {
            throw new ServiceException("专家接单结果已更新，请刷新后重试");
        }
        if ("ACCEPTED".equals(acceptanceStatus) && "CONFIRMED".equals(conflictStatus))
        {
            throw new ServiceException("存在明确利益冲突时不能直接接受任务");
        }
        if (taskConflictMapper.confirm(confirmationId, userId, acceptanceStatus,
                conflictStatus,
                action.getStatement().trim(), action.getVersion()) == 0)
        {
            throw new ServiceException("专家接单结果已更新，请刷新后重试");
        }
        return taskConflictMapper.selectById(confirmationId);
    }

    @Transactional
    public ExpertTaskConflictConfirmation confirmArrangement(Long confirmationId,
            String operator, String sourceSystem)
    {
        ExpertTaskConflictConfirmation confirmation =
                taskConflictMapper.selectByIdForUpdate(confirmationId);
        if (confirmation == null)
        {
            throw new ServiceException("专家接单请求不存在");
        }
        if (!"ACCEPTED".equals(confirmation.getStatus()))
        {
            throw new ServiceException("专家尚未接受任务，不能确认正式安排");
        }
        if ("CONFIRMED".equals(confirmation.getConflictStatus()))
        {
            throw new ServiceException("任务存在明确利益冲突，不能确认正式安排");
        }
        if ("CONFIRMED".equals(confirmation.getArrangementStatus()))
        {
            return confirmation;
        }
        if ("CANCELLED".equals(confirmation.getArrangementStatus()))
        {
            throw new ServiceException("正式安排已取消，如需重新安排请重新发起接单");
        }
        String source = StringUtils.isEmpty(sourceSystem)
                ? "business-workspace" : sourceSystem;
        String sourceVersion = "arrangement-" + confirmationId;
        if (taskConflictMapper.updateArrangement(confirmationId, "NOT_CONFIRMED",
                "CONFIRMED", operator, source, sourceVersion,
                confirmation.getVersion()) == 0)
        {
            throw new ServiceException("正式安排已被处理，请刷新后重试");
        }
        taskConflictMapper.upsertOccupancySnapshot(
                confirmation.getUserId(), confirmation.getExternalTaskId(),
                confirmation.getServiceStart(), confirmation.getServiceEnd(),
                confirmation.getServiceMode(), confirmation.getServiceRegion(),
                confirmation.getIndustry(), "OCCUPIED",
                confirmation.getConflictStatus(), source,
                "EXTERNAL", sourceVersion, operator);
        return taskConflictMapper.selectById(confirmationId);
    }

    @Transactional
    public ExpertTaskConflictConfirmation cancelArrangement(Long confirmationId,
            String operator, String sourceSystem)
    {
        ExpertTaskConflictConfirmation confirmation =
                taskConflictMapper.selectByIdForUpdate(confirmationId);
        if (confirmation == null)
        {
            throw new ServiceException("专家接单请求不存在");
        }
        if (!"CONFIRMED".equals(confirmation.getArrangementStatus()))
        {
            return confirmation;
        }
        String source = StringUtils.isEmpty(sourceSystem)
                ? "business-workspace" : sourceSystem;
        String sourceVersion = "arrangement-" + confirmationId;
        if (taskConflictMapper.updateArrangement(confirmationId, "CONFIRMED",
                "CANCELLED", operator, source, sourceVersion,
                confirmation.getVersion()) == 0)
        {
            throw new ServiceException("正式安排已被处理，请刷新后重试");
        }
        taskConflictMapper.upsertOccupancySnapshot(
                confirmation.getUserId(), confirmation.getExternalTaskId(),
                confirmation.getServiceStart(), confirmation.getServiceEnd(),
                confirmation.getServiceMode(), confirmation.getServiceRegion(),
                confirmation.getIndustry(), "FREE",
                confirmation.getConflictStatus(), source,
                "EXTERNAL", sourceVersion, operator);
        return taskConflictMapper.selectById(confirmationId);
    }

    @Transactional(readOnly = true)
    public TaskConflictResult resolve(String externalTaskId, Long userId,
            LocalDateTime serviceStart, LocalDateTime serviceEnd)
    {
        if (StringUtils.isEmpty(externalTaskId))
        {
            return new TaskConflictResult("NO_TASK_CONTEXT", "NOT_REQUESTED",
                    "UNKNOWN", null);
        }
        ExpertTaskConflictConfirmation confirmation =
                taskConflictMapper.selectLatestByTask(
                        externalTaskId, userId,
                        serviceStart.format(DATETIME_FORMAT),
                        serviceEnd.format(DATETIME_FORMAT));
        if (confirmation == null)
        {
            return new TaskConflictResult("NOT_REQUESTED", "NOT_REQUESTED",
                    "UNKNOWN", null);
        }
        if ("PENDING".equals(confirmation.getStatus()))
        {
            return new TaskConflictResult("PENDING", "PENDING", "POTENTIAL",
                    confirmation.getConfirmationId());
        }
        return new TaskConflictResult(upper(confirmation.getStatus()),
                upper(confirmation.getStatus()),
                upper(confirmation.getConflictStatus()),
                confirmation.getConfirmationId());
    }

    private static String normalizeDateTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            throw new ServiceException("任务时间不能为空");
        }
        String normalized = value.trim().replace('T', ' ');
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"))
        {
            try
            {
                return LocalDateTime.parse(normalized,
                        DateTimeFormatter.ofPattern(pattern)).format(DATETIME_FORMAT);
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种允许的输入格式。
            }
        }
        throw new ServiceException("任务时间格式不正确");
    }

    private static LocalDateTime parseDateTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"))
        {
            try
            {
                return LocalDateTime.parse(normalized,
                        DateTimeFormatter.ofPattern(pattern));
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种允许的输入格式。
            }
        }
        return null;
    }

    private void validateDeclaredAvailability(Long userId, LocalDateTime start,
            LocalDateTime end, String requestedMode)
    {
        List<ExpertSchedule> schedules = platformMapper.selectScheduleByUserId(userId);
        LocalDateTime cursor = start;
        while (cursor.isBefore(end))
        {
            LocalDate date = cursor.toLocalDate();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            if (end.isBefore(dayEnd))
            {
                dayEnd = end;
            }
            if (!dayCovered(schedules, date, cursor, dayEnd, requestedMode))
            {
                throw new ServiceException("该时间不在专家常规可服务时间内，不能发起接单");
            }
            cursor = dayEnd;
        }
    }

    private static boolean dayCovered(List<ExpertSchedule> schedules, LocalDate date,
            LocalDateTime start, LocalDateTime end, String requestedMode)
    {
        DayOfWeek weekday = date.getDayOfWeek();
        List<ScheduleRange> ranges = new ArrayList<>();
        for (ExpertSchedule schedule : schedules)
        {
            if (schedule.getWeekday() == null
                    || schedule.getWeekday() != weekday.getValue()
                    || schedule.getWeeklyCapacity() == null
                    || schedule.getWeeklyCapacity() <= 0
                    || !effectiveOn(schedule, date)
                    || !modeMatches(schedule, requestedMode))
            {
                continue;
            }
            LocalTime rangeStart = parseTime(schedule.getStartTime());
            LocalTime rangeEnd = parseTime(schedule.getEndTime());
            if (rangeStart != null && rangeEnd != null && rangeEnd.isAfter(rangeStart))
            {
                ranges.add(new ScheduleRange(date.atTime(rangeStart), date.atTime(rangeEnd)));
            }
        }
        ranges.sort(Comparator.comparing(ScheduleRange::start));
        LocalDateTime cursor = start;
        for (ScheduleRange range : ranges)
        {
            if (!range.end().isAfter(cursor))
            {
                continue;
            }
            if (range.start().isAfter(cursor))
            {
                return false;
            }
            cursor = range.end();
            if (!cursor.isBefore(end))
            {
                return true;
            }
        }
        return !cursor.isBefore(end);
    }

    private void validateNoTemporaryUnavailable(Long userId, LocalDateTime start,
            LocalDateTime end)
    {
        for (ExpertUnavailablePeriod period :
                platformMapper.selectUnavailablePeriodsByUserId(userId))
        {
            LocalDateTime unavailableStart = parseDateTime(period.getStartTime());
            LocalDateTime unavailableEnd = parseDateTime(period.getEndTime());
            if (unavailableStart != null && unavailableEnd != null
                    && unavailableStart.isBefore(end) && unavailableEnd.isAfter(start))
            {
                throw new ServiceException("该时间与专家临时不可用时段重叠，不能发起接单");
            }
        }
    }

    private static boolean effectiveOn(ExpertSchedule schedule, LocalDate date)
    {
        if (StringUtils.isEmpty(schedule.getEffectiveFrom()))
        {
            return true;
        }
        try
        {
            return !date.isBefore(LocalDate.parse(schedule.getEffectiveFrom()
                    .substring(0, 10)));
        }
        catch (Exception ex)
        {
            return true;
        }
    }

    private static boolean modeMatches(ExpertSchedule schedule, String requestedMode)
    {
        if (StringUtils.isEmpty(requestedMode) || StringUtils.isEmpty(schedule.getServiceMode()))
        {
            return true;
        }
        String mode = upper(schedule.getServiceMode());
        return "BOTH".equals(mode) || "BOTH".equals(requestedMode)
                || mode.equals(requestedMode);
    }

    private static LocalTime parseTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        try
        {
            return LocalTime.parse(value.trim());
        }
        catch (DateTimeParseException ex)
        {
            return null;
        }
    }

    private static String upper(String value)
    {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private record ScheduleRange(LocalDateTime start, LocalDateTime end)
    {
    }

    public record TaskConflictResult(String confirmationStatus,
            String acceptanceStatus, String conflictStatus, Long confirmationId)
    {
    }
}
