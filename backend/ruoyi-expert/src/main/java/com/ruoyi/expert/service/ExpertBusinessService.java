package com.ruoyi.expert.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 业务经办人员的专家只读查询。
 */
@Service
public class ExpertBusinessService
{
    @Autowired
    private ExpertBusinessMapper businessMapper;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertOpenApiService openApiService;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listExperts(Map<String, Object> query)
    {
        return businessMapper.selectBusinessExpertList(query);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> expertDetail(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        ensureEffectiveProfile(profile);

        List<ExpertSchedule> schedules = platformMapper.selectScheduleByUserId(userId);
        Map<String, Object> latestRelease =
                platformMapper.selectLatestExpertReleaseVersion(userId);
        Map<String, Object> profileSnapshot = parseObject(
                latestRelease == null ? null : latestRelease.get("profileSnapshotJson"));
        List<Map<String, Object>> dimensions = parseDimensionList(
                profileSnapshot.get("dimensions"));
        if (dimensions.isEmpty())
        {
            // 兼容版本快照上线前的已认证专家；不生成版本，也不写入数据库。
            dimensions = dimensionViewService.dimensions(userId, "DRAFT").stream()
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
        List<Map<String, Object>> conflictDimensions = dimensions.stream()
                .filter(item -> "CONFLICT".equals(item.get("groupCode"))
                        || String.valueOf(item.get("dimensionCode")).startsWith("CONFLICT_"))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readOnly", true);
        result.put("expert", profileView(profile));
        result.put("latestRelease", releaseView(latestRelease));
        result.put("publishedProfile", profileSnapshot.get("profile"));
        result.put("dimensions", dimensions);
        result.put("schedules", schedules);
        result.put("conflict", conflictView(
                businessMapper.selectLatestConflictDisclosure(userId),
                conflictDimensions));
        result.put("notice",
                "该页面仅供业务查询，不提供专家档案、时间、画像或利益冲突声明的修改入口。");
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> checkTaskEligibility(ExpertEligibilityRequest request)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(request.getUserId());
        ensureEffectiveProfile(profile);
        return openApiService.checkEligibility(request);
    }

    @Transactional(readOnly = true)
    public void ensureAcceptanceRequestEligible(ExpertEligibilityRequest request)
    {
        Map<String, Object> result = checkTaskEligibility(request);
        if (ExpertOpenApiService.INELIGIBLE.equals(result.get("decision")))
        {
            throw new ServiceException("本次业务核验未通过，不能发起专家接单");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> occupancySnapshots(Long userId,
            String serviceStart, String serviceEnd)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        ensureEffectiveProfile(profile);
        if (StringUtils.isEmpty(serviceStart) || StringUtils.isEmpty(serviceEnd))
        {
            throw new ServiceException("占用查询开始和结束时间不能为空");
        }
        List<Map<String, Object>> snapshots = businessMapper.selectOccupancySnapshots(
                        userId, serviceStart.trim(), serviceEnd.trim())
                .stream()
                .filter(item -> item.get("externalTaskId") instanceof String taskId
                        && StringUtils.isNotEmpty(taskId))
                .toList();
        return latestOccupancyByTaskTime(snapshots);
    }

    private static List<Map<String, Object>> latestOccupancyByTaskTime(
            List<Map<String, Object>> snapshots)
    {
        Map<String, Map<String, Object>> latestByTask = new LinkedHashMap<>();
        for (Map<String, Object> snapshot : snapshots)
        {
            String key = snapshot.get("externalTaskId") + "\u0001"
                    + snapshot.get("serviceStart") + "\u0001"
                    + snapshot.get("serviceEnd");
            Map<String, Object> current = latestByTask.get(key);
            if (current == null || contextId(snapshot) >= contextId(current))
            {
                latestByTask.put(key, snapshot);
            }
        }
        return new ArrayList<>(latestByTask.values());
    }

    private static long contextId(Map<String, Object> snapshot)
    {
        Object value = snapshot.get("contextId");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static void ensureEffectiveProfile(ExpertProfile profile)
    {
        if (profile == null || !"0".equals(profile.getDelFlag())
                || !"APPROVED".equals(profile.getReviewStatus())
                || "EXITED".equals(profile.getServiceStatus()))
        {
            throw new ServiceException("该专家没有可供业务使用的有效档案");
        }
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
        result.put("graphVersion", profile.getGraphVersion());
        result.put("profileVersion", profile.getVersion());
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

    private static Map<String, Object> conflictView(
            Map<String, Object> declaration, List<Map<String, Object>> dimensions)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskLevelRequired", true);
        if (declaration == null)
        {
            result.put("declaration", Map.of("status", "NOT_FOUND"));
        }
        else
        {
            Map<String, Object> declarationView = new LinkedHashMap<>(declaration);
            boolean expired = isExpired(String.valueOf(declaration.get("validUntil")));
            declarationView.put("validityStatus", expired ? "EXPIRED" : "CURRENT");
            result.put("declaration", declarationView);
        }
        result.put("dimensions", dimensions);
        result.put("message", declaration == null
                ? "未找到已审核通过的利益冲突说明；具体任务仍需进行任务级核验。"
                : "EXPIRED".equals(((Map<?, ?>) result.get("declaration")).get("validityStatus"))
                ? "最近一份利益冲突说明已过期；具体任务必须重新核验。"
                : "利益冲突说明已审核通过；具体任务仍需结合任务相对方进行核验。");
        return result;
    }

    private static boolean isExpired(String validUntil)
    {
        if (validUntil == null || "null".equals(validUntil) || validUntil.isBlank())
        {
            return false;
        }
        try
        {
            String date = validUntil.length() > 10 ? validUntil.substring(0, 10) : validUntil;
            return LocalDate.parse(date).isBefore(LocalDate.now());
        }
        catch (DateTimeParseException ex)
        {
            return false;
        }
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
