package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 面向课题二匹配与调度流程的完整专家事实快照。
 */
@Service
public class ExpertIntegrationSnapshotService
{
    private static final String CONTRACT_VERSION = "expert-domain-v1";

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertBusinessMapper businessMapper;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    @Transactional(readOnly = true)
    public Map<String, Object> snapshot(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            return unavailable(userId, "专家档案不存在");
        }
        boolean availableForMatching = "APPROVED".equals(profile.getReviewStatus())
                && !"EXITED".equals(profile.getServiceStatus());
        String unavailableReason = availableForMatching ? null
                : "APPROVED".equals(profile.getReviewStatus())
                ? "专家已退出专家库" : "专家尚未正式认证";

        List<ExpertSchedule> schedules = platformMapper.selectScheduleByUserId(userId);
        List<ExpertUnavailablePeriod> unavailablePeriods =
                platformMapper.selectUnavailablePeriodsByUserId(userId);
        Map<String, Object> latestRelease =
                platformMapper.selectLatestExpertReleaseVersion(userId);
        Map<String, Object> profileSnapshot = parseObject(
                latestRelease == null ? null : latestRelease.get("profileSnapshotJson"));
        List<Map<String, Object>> dimensions =
                parseDimensionList(profileSnapshot.get("dimensions"));
        if (dimensions.isEmpty())
        {
            dimensions = currentDimensions(userId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractVersion", CONTRACT_VERSION);
        result.put("userId", profile.getUserId());
        result.put("availableForMatching", availableForMatching);
        result.put("unavailableReason", unavailableReason);
        result.put("profile", profileView(profile));
        result.put("profileVersion", profile.getVersion());
        result.put("graphVersion", profile.getGraphVersion());
        result.put("latestRelease", releaseView(latestRelease));
        result.put("capabilityTags", capabilityTags(profile, dimensions));
        result.put("dimensions", dimensions);
        result.put("schedules", schedules);
        result.put("unavailablePeriods", unavailablePeriods.stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("startTime", item.getStartTime());
                    row.put("endTime", item.getEndTime());
                    return row;
                })
                .toList());
        result.put("conflict", conflictView(
                businessMapper.selectLatestConflictDisclosure(userId)));
        result.put("activeAssignmentsKnown", false);
        result.put("activeAssignmentsNotice",
                "正式任务占用由课题二匹配调度流程在具体任务核验时提供。");
        result.put("matchingDecision", false);
        result.put("dispatchDecision", false);
        result.put("dataVersion", profile.getUserId() + ":"
                + (profile.getVersion() == null ? 0 : profile.getVersion()) + ":"
                + (profile.getGraphVersion() == null ? 0 : profile.getGraphVersion()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> batch(List<Long> userIds)
    {
        Set<Long> uniqueIds = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        return uniqueIds.stream().map(this::snapshot).toList();
    }

    private Map<String, Object> unavailable(Long userId, String reason)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contractVersion", CONTRACT_VERSION);
        result.put("userId", userId);
        result.put("availableForMatching", false);
        result.put("unavailableReason", reason);
        result.put("matchingDecision", false);
        result.put("dispatchDecision", false);
        return result;
    }

    private static Map<String, Object> profileView(ExpertProfile profile)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("profileId", profile.getProfileId());
        result.put("userId", profile.getUserId());
        result.put("realName", profile.getRealName());
        result.put("organization", profile.getOrganization());
        result.put("professionalTitle", profile.getProfessionalTitle());
        result.put("researchField", profile.getResearchField());
        result.put("industryField", profile.getIndustryField());
        result.put("serviceRegion", profile.getServiceRegion());
        result.put("careerSummary", profile.getCareerSummary());
        result.put("reviewStatus", profile.getReviewStatus());
        result.put("serviceStatus", profile.getServiceStatus());
        result.put("creditScore", profile.getCreditScore());
        result.put("approvedTime", profile.getApprovedTime());
        return result;
    }

    private static Map<String, Object> releaseView(Map<String, Object> release)
    {
        if (release == null)
        {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("releaseId", release.get("releaseId"));
        result.put("versionNo", release.get("versionNo"));
        result.put("graphVersion", release.get("graphVersion"));
        result.put("approvedBy", release.get("approvedBy"));
        result.put("triggerType", release.get("triggerType"));
        result.put("createTime", release.get("createTime"));
        return result;
    }

    private static List<Map<String, Object>> capabilityTags(ExpertProfile profile,
            List<Map<String, Object>> dimensions)
    {
        List<Map<String, Object>> tags = new ArrayList<>();
        addTag(tags, "INDUSTRY", profile.getIndustryField());
        addTag(tags, "RESEARCH_FIELD", profile.getResearchField());
        addTag(tags, "SERVICE_REGION", profile.getServiceRegion());
        for (Map<String, Object> dimension : dimensions)
        {
            if ("INSUFFICIENT_EVIDENCE".equals(dimension.get("dataStatus")))
            {
                continue;
            }
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("tagType", "CAPABILITY");
            tag.put("code", dimension.get("dimensionCode"));
            tag.put("name", dimension.get("dimensionName"));
            tag.put("groupCode", dimension.get("groupCode"));
            tag.put("score", dimension.get("score"));
            tag.put("dataStatus", dimension.get("dataStatus"));
            tags.add(tag);
        }
        return tags;
    }

    private static void addTag(List<Map<String, Object>> tags, String type, String name)
    {
        if (StringUtils.isEmpty(name))
        {
            return;
        }
        Map<String, Object> tag = new LinkedHashMap<>();
        tag.put("tagType", type);
        tag.put("name", name);
        tags.add(tag);
    }

    private static Map<String, Object> conflictView(Map<String, Object> declaration)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("declarationFound", declaration != null);
        result.put("declaration", declaration == null
                ? Map.of("status", "NOT_FOUND") : declaration);
        result.put("taskLevelRequired", true);
        result.put("message", declaration == null
                ? "未找到已审核利益冲突说明，具体任务必须人工核验。"
                : "利益冲突声明可用，具体任务仍需由匹配流程核验。");
        return result;
    }

    private List<Map<String, Object>> currentDimensions(Long userId)
    {
        return dimensionViewService.dimensions(userId, "DRAFT").stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dimensionCode", item.getDimensionCode());
                    row.put("groupCode", item.getGroupCode());
                    row.put("groupName", item.getGroupName());
                    row.put("dimensionName", item.getDimensionName());
                    row.put("description", item.getDescription());
                    row.put("score", item.getScore());
                    row.put("confidence", item.getConfidence());
                    row.put("dataStatus", item.getDataStatus());
                    row.put("evidence", item.getEvidence());
                    return row;
                })
                .toList();
    }

    private static Map<String, Object> parseObject(Object value)
    {
        if (value == null)
        {
            return new LinkedHashMap<>();
        }
        try
        {
            JSONObject object = JSON.parseObject(String.valueOf(value));
            return object == null ? new LinkedHashMap<>() : new LinkedHashMap<>(object);
        }
        catch (Exception ex)
        {
            return new LinkedHashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseDimensionList(Object value)
    {
        if (!(value instanceof JSONArray array))
        {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : array)
        {
            if (item instanceof Map<?, ?>)
            {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }
}
