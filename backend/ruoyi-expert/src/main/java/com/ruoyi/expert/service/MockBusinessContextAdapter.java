package com.ruoyi.expert.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertBusinessContextSnapshot;
import com.ruoyi.expert.mapper.ExpertOpenApiMapper;

/**
 * 基于数据库业务快照的适配器。
 *
 * 当前部署只写入人工导入或模拟数据。没有快照时返回 UNKNOWN，不能推断为实际空闲。
 */
@Component
public class MockBusinessContextAdapter implements BusinessContextPort
{
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ExpertOpenApiMapper openApiMapper;

    @Override
    public BusinessContextResult inspect(BusinessContextQuery query)
    {
        List<ExpertBusinessContextSnapshot> snapshots =
                openApiMapper.selectOverlappingBusinessContexts(
                        query.userId(),
                        query.serviceStart().format(DATETIME_FORMAT),
                        query.serviceEnd().format(DATETIME_FORMAT));
        if (snapshots == null || snapshots.isEmpty())
        {
            return new BusinessContextResult("UNKNOWN", "UNKNOWN",
                    "MOCK_OR_MANUAL_SNAPSHOT", null, List.of());
        }
        snapshots = snapshots
                .stream()
                .filter(item -> includeSnapshot(query, item))
                .filter(item -> matchesMode(item, query.serviceMode()))
                .filter(item -> matchesText(item.getServiceRegion(), query.serviceRegion()))
                .filter(item -> matchesText(item.getIndustry(), query.industry()))
                .toList();
        if (snapshots == null || snapshots.isEmpty())
        {
            return new BusinessContextResult("UNKNOWN", "UNKNOWN",
                    "MOCK_OR_MANUAL_SNAPSHOT", null, List.of());
        }

        String occupancyStatus = resolveOccupancy(snapshots);
        String conflictStatus = resolveConflict(snapshots);
        Set<String> sources = new LinkedHashSet<>();
        Set<String> taskRefs = new LinkedHashSet<>();
        String verifiedAt = null;
        for (ExpertBusinessContextSnapshot snapshot : snapshots)
        {
            if (StringUtils.isNotEmpty(snapshot.getSourceKind()))
            {
                sources.add(snapshot.getSourceKind());
            }
            if (StringUtils.isNotEmpty(snapshot.getSourceSystem()))
            {
                sources.add(snapshot.getSourceSystem());
            }
            if (StringUtils.isNotEmpty(snapshot.getExternalTaskId()))
            {
                taskRefs.add(snapshot.getExternalTaskId());
            }
            if (verifiedAt == null && StringUtils.isNotEmpty(snapshot.getVerifiedAt()))
            {
                verifiedAt = snapshot.getVerifiedAt();
            }
        }
        return new BusinessContextResult(
                occupancyStatus,
                conflictStatus,
                sources.isEmpty() ? "MOCK_OR_MANUAL_SNAPSHOT"
                        : String.join(",", sources),
                verifiedAt,
                new ArrayList<>(taskRefs));
    }

    private static boolean includeSnapshot(BusinessContextQuery query,
            ExpertBusinessContextSnapshot snapshot)
    {
        if (StringUtils.isEmpty(query.externalTaskId())
                || !query.externalTaskId().equals(snapshot.getExternalTaskId()))
        {
            return true;
        }
        // 当前任务自己的占用记录证明的是自身安排，不代表再次派单冲突；
        // 当前任务自己的 FREE 记录仍需参与核验。其他任务的占用继续阻断。
        return !"OCCUPIED".equals(snapshot.getOccupancyStatus())
                && !"BLOCKED".equals(snapshot.getOccupancyStatus());
    }

    private static boolean matchesMode(ExpertBusinessContextSnapshot snapshot,
            String requestedMode)
    {
        if (StringUtils.isEmpty(snapshot.getServiceMode())
                || StringUtils.isEmpty(requestedMode))
        {
            return true;
        }
        String source = snapshot.getServiceMode().trim().toUpperCase();
        String requested = requestedMode.trim().toUpperCase();
        return "BOTH".equals(source) || "BOTH".equals(requested)
                || source.equals(requested);
    }

    private static boolean matchesText(String sourceValue, String requestedValue)
    {
        if (StringUtils.isEmpty(sourceValue) || StringUtils.isEmpty(requestedValue))
        {
            return true;
        }
        String source = normalize(sourceValue);
        String requested = normalize(requestedValue);
        return source.contains(requested) || requested.contains(source);
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s，,。.;；、]", "");
    }

    static String resolveOccupancy(List<ExpertBusinessContextSnapshot> snapshots)
    {
        if (snapshots.stream().anyMatch(item ->
                "OCCUPIED".equals(item.getOccupancyStatus())
                        || "BLOCKED".equals(item.getOccupancyStatus())))
        {
            return "OCCUPIED";
        }
        if (snapshots.stream().anyMatch(item ->
                "FREE".equals(item.getOccupancyStatus())))
        {
            return "FREE";
        }
        return "UNKNOWN";
    }

    static String resolveConflict(List<ExpertBusinessContextSnapshot> snapshots)
    {
        if (snapshots.stream().anyMatch(item ->
                "CONFIRMED".equals(item.getConflictStatus())))
        {
            return "CONFIRMED";
        }
        if (snapshots.stream().anyMatch(item ->
                "POTENTIAL".equals(item.getConflictStatus())))
        {
            return "POTENTIAL";
        }
        if (snapshots.stream().anyMatch(item ->
                "NONE".equals(item.getConflictStatus())))
        {
            return "NONE";
        }
        return "UNKNOWN";
    }
}
