package com.ruoyi.expert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.domain.ExpertServiceEvidenceRequest;
import com.ruoyi.expert.domain.ExpertStatusRequest;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestion;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 课题一对业务系统提供的只读核验与证据接收能力。
 */
@Service
public class ExpertOpenApiService
{
    private static final Logger log = LoggerFactory.getLogger(ExpertOpenApiService.class);

    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String INELIGIBLE = "INELIGIBLE";
    public static final String REQUIRES_MANUAL_CONFIRMATION =
            "REQUIRES_MANUAL_CONFIRMATION";

    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> EVIDENCE_TYPES = Set.of(
            "SERVICE_REPORT", "EVALUATION", "CREDIT");
    private static final Set<String> SOURCE_KINDS = Set.of(
            "EXTERNAL", "MANUAL_IMPORT", "MOCK");

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertOpenApiMapper openApiMapper;

    @Autowired
    private BusinessContextPort businessContextPort;

    @Autowired
    private ExpertProfileUpdateService profileUpdateService;

    @Autowired
    private ExpertRiskReviewService riskReviewService;

    @Autowired
    private ExpertTaskConflictService taskConflictService;

    @Transactional(readOnly = true)
    public Map<String, Object> checkEligibility(ExpertEligibilityRequest request)
    {
        LocalDateTime serviceStart = parseRequiredDateTime(request.getServiceStart(),
                "服务开始时间");
        LocalDateTime serviceEnd = parseRequiredDateTime(request.getServiceEnd(),
                "服务结束时间");
        if (!serviceStart.isBefore(serviceEnd))
        {
            throw new ServiceException("服务结束时间必须晚于服务开始时间");
        }

        ExpertProfile profile = platformMapper.selectProfileByUserId(request.getUserId());
        List<ExpertSchedule> schedules = profile == null ? List.of()
                : platformMapper.selectScheduleByUserId(request.getUserId());
        List<ExpertUnavailablePeriod> unavailablePeriods = profile == null ? List.of()
                : platformMapper.selectUnavailablePeriodsByUserId(request.getUserId());
        ExpertStatusRequest pendingExit = profile == null ? null
                : platformMapper.selectPendingStatusRequestByUserId(request.getUserId());

        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        boolean hardGatePassed = true;

        hardGatePassed &= addCheck(checks, reasons, "PROFILE",
                profile != null, "专家档案存在", "专家档案不存在");
        hardGatePassed &= addCheck(checks, reasons, "CERTIFICATION",
                profile != null && "APPROVED".equals(profile.getReviewStatus()),
                "正式认证已通过", "正式认证未通过");
        hardGatePassed &= addCheck(checks, reasons, "SERVICE_STATUS",
                profile != null && "AVAILABLE".equals(profile.getServiceStatus()),
                "当前允许接单", "当前不允许接单，状态为 "
                        + (profile == null ? "UNKNOWN" : profile.getServiceStatus()));

        String serviceMode = upper(request.getServiceMode());
        if (StringUtils.isNotEmpty(request.getServiceRegion()))
        {
            hardGatePassed &= addCheck(checks, reasons, "SERVICE_REGION",
                    profile != null && textMatches(profile.getServiceRegion(),
                            request.getServiceRegion()),
                    "服务区域覆盖 " + request.getServiceRegion(),
                    "服务区域未覆盖 " + request.getServiceRegion());
        }
        if (StringUtils.isNotEmpty(request.getIndustry()))
        {
            hardGatePassed &= addCheck(checks, reasons, "INDUSTRY",
                    profile != null && textMatches(profile.getIndustryField(),
                            request.getIndustry()),
                    "产业方向覆盖 " + request.getIndustry(),
                    "产业方向未覆盖 " + request.getIndustry());
        }

        boolean scheduleCovered = profile != null && schedulesCover(
                schedules, serviceStart, serviceEnd, serviceMode);
        hardGatePassed &= addCheck(checks, reasons, "DECLARED_SCHEDULE",
                scheduleCovered, "常规可服务时间覆盖本次任务",
                "常规可服务时间未覆盖本次任务");

        List<ExpertUnavailablePeriod> overlappingUnavailable = unavailablePeriods.stream()
                .filter(item -> overlaps(item.getStartTime(), item.getEndTime(),
                        serviceStart, serviceEnd))
                .toList();
        hardGatePassed &= addCheck(checks, reasons, "TEMPORARY_UNAVAILABILITY",
                overlappingUnavailable.isEmpty(), "无临时不可用时段重叠",
                "与专家临时不可用时段重叠");

        BusinessContextPort.BusinessContextResult context = businessContextPort.inspect(
                new BusinessContextPort.BusinessContextQuery(
                        request.getUserId(), request.getExternalTaskId(),
                        serviceStart, serviceEnd, serviceMode,
                        request.getServiceRegion(), request.getIndustry()));
        String occupancyStatus = upper(context.occupancyStatus());
        String snapshotConflictStatus = upper(context.conflictStatus());
        ExpertTaskConflictService.TaskConflictResult taskConflict =
                taskConflictService.resolve(request.getExternalTaskId(),
                        request.getUserId(), serviceStart, serviceEnd);
        String acceptanceStatus = upper(taskConflict.acceptanceStatus());
        boolean taskRejected = "REJECTED".equals(acceptanceStatus);
        addBusinessCheck(checks, reasons, "TASK_ACCEPTANCE",
                taskRejected ? "FAIL" : "ACCEPTED".equals(acceptanceStatus)
                        ? "PASS" : "WARN",
                taskRejected ? "专家已明确拒绝本次任务"
                        : "ACCEPTED".equals(acceptanceStatus) ? "专家已接受本次任务"
                        : "专家尚未确认是否接受本次任务");
        String conflictStatus = mergeConflictStatus(
                snapshotConflictStatus, taskConflict);

        boolean occupied = "OCCUPIED".equals(occupancyStatus)
                || "BLOCKED".equals(occupancyStatus);
        boolean occupancyKnown = "FREE".equals(occupancyStatus) || occupied;
        addBusinessCheck(checks, reasons, "BUSINESS_OCCUPANCY",
                occupied ? "FAIL" : occupancyKnown ? "PASS" : "WARN",
                occupied ? "该专家在这段时间已有其他安排，不能重复安排"
                        : occupancyKnown ? "该专家在这段时间目前没有其他安排"
                        : "系统暂时无法确认这段时间是否已有安排，请人工核实");

        boolean conflictConfirmed = "CONFIRMED".equals(conflictStatus);
        boolean conflictPotential = "POTENTIAL".equals(conflictStatus);
        boolean conflictClear = "NONE".equals(conflictStatus);
        addBusinessCheck(checks, reasons, "TASK_CONFLICT",
                conflictConfirmed ? "FAIL" : conflictClear ? "PASS" : "WARN",
                conflictConfirmed ? "存在已确认的任务利益冲突"
                        : conflictPotential ? "存在潜在利益冲突，需要人工确认"
                        : conflictClear ? "未发现任务利益冲突"
                        : "没有任务级利益冲突核验结果");

        if (pendingExit != null)
        {
            addBusinessCheck(checks, reasons, "PENDING_EXIT", "WARN",
                    "专家退出申请正在审核，当前仍按既有接单状态处理");
        }

        String decision;
        if (!hardGatePassed || occupied || conflictConfirmed || taskRejected)
        {
            decision = INELIGIBLE;
        }
        else if (!occupancyKnown || !conflictClear || conflictPotential
                || !"ACCEPTED".equals(acceptanceStatus))
        {
            decision = REQUIRES_MANUAL_CONFIRMATION;
        }
        else
        {
            decision = ELIGIBLE;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("decision", decision);
        result.put("asOf", LocalDateTime.now().format(DATETIME_FORMAT));
        result.put("userId", request.getUserId());
        result.put("manualConfirmationRequired",
                REQUIRES_MANUAL_CONFIRMATION.equals(decision));
        result.put("candidateRanked", false);
        result.put("profileVersion", profile == null ? null : profile.getVersion());
        result.put("reviewStatus", profile == null ? null : profile.getReviewStatus());
        result.put("serviceStatus", profile == null ? null : profile.getServiceStatus());
        result.put("creditScore", profile == null ? null : profile.getCreditScore());
        result.put("checks", checks);
        result.put("reasons", new ArrayList<>(new LinkedHashSet<>(reasons)));
        result.put("taskContext", taskContext(request, serviceStart, serviceEnd));
        result.put("businessContext", businessContext(context));
        result.put("taskConflictConfirmation", Map.of(
                "status", taskConflict.confirmationStatus(),
                "acceptanceStatus", acceptanceStatus,
                "conflictStatus", taskConflict.conflictStatus(),
                "confirmationId", taskConflict.confirmationId() == null
                        ? "" : taskConflict.confirmationId()));
        result.put("notice",
                "模拟或人工快照只用于联调；正式派单前必须由真实派单系统确认任务占用和任务级冲突。");
        return result;
    }

    public Map<String, Object> ingestServiceEvidence(ExpertServiceEvidenceRequest request,
            String receivedBy)
    {
        String evidenceType = upper(request.getEvidenceType());
        if (!EVIDENCE_TYPES.contains(evidenceType))
        {
            throw new ServiceException("证据类型仅支持 SERVICE_REPORT、EVALUATION、CREDIT");
        }
        String sourceKind = StringUtils.isEmpty(request.getSourceKind())
                ? "MANUAL_IMPORT" : upper(request.getSourceKind());
        if (!SOURCE_KINDS.contains(sourceKind))
        {
            throw new ServiceException("来源类型仅支持 EXTERNAL、MANUAL_IMPORT、MOCK");
        }
        ExpertProfile profile = platformMapper.selectProfileByUserId(request.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在，不能接收业务证据");
        }

        ExpertServiceEvidence existing = openApiMapper
                .selectServiceEvidenceBySourceEventId(request.getSourceEventId());
        if (existing != null)
        {
            return evidenceResult(existing, true);
        }

        LocalDateTime occurredAt = parseOptionalDateTime(request.getOccurredAt(),
                "证据发生时间");
        if (occurredAt == null)
        {
            occurredAt = LocalDateTime.now();
        }
        ExpertServiceEvidence evidence = new ExpertServiceEvidence();
        evidence.setSourceEventId(request.getSourceEventId());
        evidence.setUserId(request.getUserId());
        evidence.setEvidenceType(evidenceType);
        evidence.setExternalTaskId(request.getExternalTaskId());
        evidence.setSourceSystem(request.getSourceSystem());
        evidence.setSourceKind(sourceKind);
        evidence.setOccurredAt(occurredAt.format(DATETIME_FORMAT));
        evidence.setTitle(request.getTitle());
        evidence.setSummary(request.getSummary());
        evidence.setRawPayload(request.getRawPayload());
        evidence.setSourceDigest(StringUtils.isNotEmpty(request.getSourceDigest())
                ? request.getSourceDigest() : digest(request.getRawPayload()));
        evidence.setProcessingStatus("RECEIVED");
        evidence.setReceivedBy(receivedBy);
        evidence.setCreateBy(receivedBy);
        openApiMapper.insertServiceEvidence(evidence);
        Long suggestionId = null;
        Long riskReviewId = null;
        try
        {
            var risk = riskReviewService.createFromEvidence(evidence);
            riskReviewId = risk == null ? null : risk.getRiskId();
        }
        catch (Exception ex)
        {
            log.warn("风险预警生成失败，evidenceId={}", evidence.getEvidenceId(), ex);
        }
        try
        {
            ExpertProfileUpdateSuggestion suggestion =
                    profileUpdateService.generate(evidence);
            if (suggestion != null)
            {
                suggestionId = suggestion.getSuggestionId();
                openApiMapper.updateServiceEvidenceStatus(evidence.getEvidenceId(), "SUGGESTED");
                evidence.setProcessingStatus("SUGGESTED");
            }
        }
        catch (Exception ex)
        {
            // 证据接收成功优先于建议生成；失败时保留 RECEIVED，等待管理员重试。
            log.warn("画像更新建议生成失败，evidenceId={}", evidence.getEvidenceId(), ex);
        }
        Map<String, Object> result = evidenceResult(evidence, false);
        result.put("suggestionId", suggestionId);
        result.put("riskReviewId", riskReviewId);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> expertSnapshot(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertStatusRequest pendingExit =
                platformMapper.selectPendingStatusRequestByUserId(userId);
        List<ExpertSchedule> schedules = platformMapper.selectScheduleByUserId(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", profile.getUserId());
        result.put("realName", profile.getRealName());
        result.put("organization", profile.getOrganization());
        result.put("professionalTitle", profile.getProfessionalTitle());
        result.put("researchField", profile.getResearchField());
        result.put("industryField", profile.getIndustryField());
        result.put("serviceRegion", profile.getServiceRegion());
        result.put("reviewStatus", profile.getReviewStatus());
        result.put("serviceStatus", profile.getServiceStatus());
        result.put("creditScore", profile.getCreditScore());
        result.put("profileVersion", profile.getVersion());
        result.put("graphVersion", profile.getGraphVersion());
        result.put("published", "APPROVED".equals(profile.getReviewStatus()));
        result.put("pendingExit", pendingExit != null);
        result.put("schedules", schedules);
        result.put("availabilityMeaning", "DECLARED_SERVICE_TIME_ONLY");
        result.put("notice",
                "该接口不返回实际任务占用；派单前必须调用资格核验接口并结合真实调度系统确认。");
        return result;
    }

    private static Map<String, Object> taskContext(ExpertEligibilityRequest request,
            LocalDateTime serviceStart, LocalDateTime serviceEnd)
    {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("externalTaskId", request.getExternalTaskId());
        task.put("taskType", request.getTaskType());
        task.put("serviceStart", serviceStart.format(DATETIME_FORMAT));
        task.put("serviceEnd", serviceEnd.format(DATETIME_FORMAT));
        task.put("serviceMode", upper(request.getServiceMode()));
        task.put("serviceRegion", request.getServiceRegion());
        task.put("industry", request.getIndustry());
        return task;
    }

    private static Map<String, Object> businessContext(
            BusinessContextPort.BusinessContextResult context)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("occupancyStatus", upper(context.occupancyStatus()));
        result.put("conflictStatus", upper(context.conflictStatus()));
        result.put("source", context.source());
        result.put("verifiedAt", context.verifiedAt());
        result.put("taskRefs", context.taskRefs());
        return result;
    }

    private static String mergeConflictStatus(String snapshotStatus,
            ExpertTaskConflictService.TaskConflictResult taskConflict)
    {
        String taskStatus = upper(taskConflict.conflictStatus());
        if ("CONFIRMED".equals(snapshotStatus) || "CONFIRMED".equals(taskStatus))
        {
            return "CONFIRMED";
        }
        if ("POTENTIAL".equals(snapshotStatus) || "PENDING".equals(taskStatus))
        {
            return "POTENTIAL";
        }
        if ("NONE".equals(taskStatus) || "NONE".equals(snapshotStatus))
        {
            return "NONE";
        }
        return "UNKNOWN";
    }

    private static Map<String, Object> evidenceResult(ExpertServiceEvidence evidence,
            boolean duplicate)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evidenceId", evidence.getEvidenceId());
        result.put("sourceEventId", evidence.getSourceEventId());
        result.put("duplicate", duplicate);
        result.put("processingStatus", evidence.getProcessingStatus());
        result.put("sourceKind", evidence.getSourceKind());
        result.put("sourceDigest", evidence.getSourceDigest());
        result.put("profileUpdated", false);
        result.put("notice", "证据已接收；画像仅在人工审核批准后才会更新。");
        return result;
    }

    private static boolean addCheck(List<Map<String, Object>> checks,
            List<String> reasons, String code, boolean passed, String passMessage,
            String failMessage)
    {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("code", code);
        check.put("status", passed ? "PASS" : "FAIL");
        check.put("message", passed ? passMessage : failMessage);
        checks.add(check);
        if (!passed)
        {
            reasons.add(failMessage);
        }
        return passed;
    }

    private static void addBusinessCheck(List<Map<String, Object>> checks,
            List<String> reasons, String code, String status, String message)
    {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("code", code);
        check.put("status", status);
        check.put("message", message);
        checks.add(check);
        if (!"PASS".equals(status))
        {
            reasons.add(message);
        }
    }

    private static boolean schedulesCover(List<ExpertSchedule> schedules,
            LocalDateTime serviceStart, LocalDateTime serviceEnd, String requestedMode)
    {
        LocalDateTime cursor = serviceStart;
        while (cursor.isBefore(serviceEnd))
        {
            LocalDate date = cursor.toLocalDate();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            if (serviceEnd.isBefore(dayEnd))
            {
                dayEnd = serviceEnd;
            }
            if (!dayCovered(schedules, date, cursor, dayEnd, requestedMode))
            {
                return false;
            }
            cursor = dayEnd;
        }
        return true;
    }

    private static boolean dayCovered(List<ExpertSchedule> schedules, LocalDate date,
            LocalDateTime start, LocalDateTime end, String requestedMode)
    {
        DayOfWeek weekday = date.getDayOfWeek();
        List<ScheduleRange> ranges = schedules.stream()
                .filter(item -> integer(item.getWeekday()) == weekday.getValue())
                .filter(item -> integer(item.getWeeklyCapacity()) > 0)
                .filter(item -> effectiveOn(item, date))
                .filter(item -> modeMatches(item, requestedMode))
                .map(item -> scheduleRange(item, date))
                .filter(item -> item != null)
                .sorted(Comparator.comparing(ScheduleRange::start))
                .toList();
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

    private static ScheduleRange scheduleRange(ExpertSchedule schedule, LocalDate date)
    {
        LocalTime start = parseTime(schedule.getStartTime());
        LocalTime end = parseTime(schedule.getEndTime());
        if (start == null || end == null || !end.isAfter(start))
        {
            return null;
        }
        return new ScheduleRange(date.atTime(start), date.atTime(end));
    }

    private static boolean effectiveOn(ExpertSchedule schedule, LocalDate date)
    {
        LocalDate effectiveFrom = parseDate(schedule.getEffectiveFrom());
        return effectiveFrom == null || !date.isBefore(effectiveFrom);
    }

    private static boolean modeMatches(ExpertSchedule schedule, String requestedMode)
    {
        if (StringUtils.isEmpty(requestedMode))
        {
            return true;
        }
        String mode = upper(schedule.getServiceMode());
        return "BOTH".equals(mode) || requestedMode.equals(mode);
    }

    private static boolean overlaps(String startValue, String endValue,
            LocalDateTime requestedStart, LocalDateTime requestedEnd)
    {
        LocalDateTime start = parseOptionalDateTime(startValue, "不可用开始时间");
        LocalDateTime end = parseOptionalDateTime(endValue, "不可用结束时间");
        return start != null && end != null
                && start.isBefore(requestedEnd) && end.isAfter(requestedStart);
    }

    private static boolean textMatches(String expertValue, String requestedValue)
    {
        String expert = normalizeText(expertValue);
        String requested = normalizeText(requestedValue);
        return !expert.isEmpty() && !requested.isEmpty()
                && (expert.contains(requested) || requested.contains(expert));
    }

    private static String normalizeText(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，,。.;；、]", "")
                .replaceAll("(壮族自治区|回族自治区|维吾尔自治区|特别行政区|自治区|省|市)$", "");
    }

    private static LocalDateTime parseRequiredDateTime(String value, String field)
    {
        LocalDateTime result = parseOptionalDateTime(value, field);
        if (result == null)
        {
            throw new ServiceException(field + "格式不正确，支持 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm");
        }
        return result;
    }

    private static LocalDateTime parseOptionalDateTime(String value, String field)
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
        throw new ServiceException(field + "格式不正确，支持 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm");
    }

    private static LocalDate parseDate(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        try
        {
            return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
        }
        catch (DateTimeParseException ignored)
        {
            return null;
        }
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
        catch (DateTimeParseException ignored)
        {
            return null;
        }
    }

    private static int integer(Integer value)
    {
        return value == null ? 0 : value;
    }

    private static String upper(String value)
    {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String digest(String value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private record ScheduleRange(LocalDateTime start, LocalDateTime end)
    {
    }
}
