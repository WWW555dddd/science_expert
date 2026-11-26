package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 专家审核版本快照：同一个发布版本同时保存当时生效的材料版本、30维画像与知识图谱。
 */
@Service
public class ExpertVersionService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    @Autowired
    private KnowledgeGraphQueryService graphQueryService;

    /**
     * 在材料审核通过后保存快照。相同生效材料组合只保留一个版本。
     */
    @Transactional
    public Map<String, Object> captureApprovedState(Long userId, String approvedBy,
            String triggerType)
    {
        List<Map<String, Object>> materials =
                platformMapper.selectEffectiveMaterialVersions(userId);
        if (materials.isEmpty())
        {
            return null;
        }
        String materialSignature = materialSignature(materials);
        Map<String, Object> latest = platformMapper.selectLatestExpertReleaseVersion(userId);
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在，无法生成审核版本");
        }
        int graphVersion = profile.getGraphVersion() == null ? 0 : profile.getGraphVersion();
        if (latest != null
                && materialSignature.equals(normalizeMaterialSignature(
                        latest.get("sourceMaterialVersionsJson")))
                && numberValue(latest.get("graphVersion")) == graphVersion)
        {
            return detail(userId, numberValue(latest.get("releaseId")));
        }

        List<ExpertGraphDimension> dimensions =
                dimensionViewService.dimensions(userId, "DRAFT");
        long availableDimensions = dimensions.stream()
                .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus()))
                .count();
        long conflictDimensions = dimensions.stream()
                .filter(item -> "CONFLICT".equals(item.getDataStatus()))
                .count();
        String status = graphVersion <= 0 ? "DRAFT"
                : !dimensions.isEmpty() && availableDimensions == dimensions.size()
                        && conflictDimensions == 0 ? "APPROVED" : "PARTIAL";
        Map<String, Object> subgraph = graphQueryService.expertSubgraph(userId, 3);

        Map<String, Object> profileSnapshot = new LinkedHashMap<>();
        profileSnapshot.put("profile", profileView(profile));
        profileSnapshot.put("status", status);
        profileSnapshot.put("graphVersion", graphVersion);
        profileSnapshot.put("availableDimensionCount", availableDimensions);
        profileSnapshot.put("insufficientDimensionCount",
                dimensions.size() - availableDimensions);
        profileSnapshot.put("dimensions", dimensions);

        int nextVersion = latest == null ? 1 : numberValue(latest.get("versionNo")).intValue() + 1;
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("userId", userId);
        release.put("versionNo", nextVersion);
        release.put("graphVersion", graphVersion);
        release.put("sourceMaterialVersionsJson", materialSignature);
        release.put("profileSnapshotJson", JSON.toJSONString(profileSnapshot));
        release.put("graphSnapshotJson", JSON.toJSONString(subgraph));
        release.put("approvedBy", approvedBy);
        release.put("triggerType", triggerType == null ? "MATERIAL_APPROVED" : triggerType);
        platformMapper.insertExpertReleaseVersion(release);
        Map<String, Object> saved = platformMapper.selectLatestExpertReleaseVersion(userId);
        return detail(userId, numberValue(saved.get("releaseId")));
    }

    /**
     * 列出时间线。对于上线前已经存在的已审核专家，在首次访问时补齐当前版本。
     */
    @Transactional
    public List<Map<String, Object>> timeline(Long userId)
    {
        ensureCurrentVersion(userId);
        return platformMapper.selectExpertReleaseVersions(userId);
    }

    @Transactional
    public Map<String, Object> detail(Long userId, Long releaseId)
    {
        Map<String, Object> row = platformMapper.selectExpertReleaseVersion(userId, releaseId);
        if (row == null)
        {
            throw new ServiceException("专家审核版本不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("releaseId", row.get("releaseId"));
        result.put("userId", row.get("userId"));
        result.put("versionNo", row.get("versionNo"));
        result.put("graphVersion", row.get("graphVersion"));
        result.put("approvedBy", row.get("approvedBy"));
        result.put("triggerType", row.get("triggerType"));
        result.put("createTime", row.get("createTime"));
        result.put("sourceMaterialVersions",
                parseValue(row.get("sourceMaterialVersionsJson"), List.of()));
        result.put("profileSnapshot",
                parseValue(row.get("profileSnapshotJson"), new HashMap<>()));
        result.put("graphSnapshot",
                parseValue(row.get("graphSnapshotJson"), new HashMap<>()));
        return result;
    }

    @Transactional
    public Map<String, Object> current(Long userId)
    {
        ensureCurrentVersion(userId);
        Map<String, Object> latest = platformMapper.selectLatestExpertReleaseVersion(userId);
        return latest == null ? null : detail(userId, numberValue(latest.get("releaseId")));
    }

    private void ensureCurrentVersion(Long userId)
    {
        List<Map<String, Object>> materials =
                platformMapper.selectEffectiveMaterialVersions(userId);
        if (materials.isEmpty())
        {
            return;
        }
        Map<String, Object> latest = platformMapper.selectLatestExpertReleaseVersion(userId);
        String signature = materialSignature(materials);
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        int graphVersion = profile == null || profile.getGraphVersion() == null
                ? 0 : profile.getGraphVersion();
        boolean materialChanged = latest == null || !Objects.equals(signature,
                normalizeMaterialSignature(latest.get("sourceMaterialVersionsJson")));
        boolean graphChanged = latest == null
                || numberValue(latest.get("graphVersion")) != graphVersion;
        if (materialChanged || graphChanged)
        {
            captureApprovedState(userId, "系统迁移", "CURRENT_STATE_BACKFILL");
        }
    }

    private static Map<String, Object> profileView(ExpertProfile profile)
    {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", profile.getUserId());
        view.put("realName", profile.getRealName());
        view.put("organization", profile.getOrganization());
        view.put("professionalTitle", profile.getProfessionalTitle());
        view.put("researchField", profile.getResearchField());
        view.put("industryField", profile.getIndustryField());
        view.put("serviceRegion", profile.getServiceRegion());
        view.put("reviewStatus", profile.getReviewStatus());
        view.put("serviceStatus", profile.getServiceStatus());
        view.put("creditScore", profile.getCreditScore());
        view.put("graphVersion", profile.getGraphVersion());
        view.put("approvedTime", profile.getApprovedTime());
        return view;
    }

    private static String materialSignature(List<Map<String, Object>> materials)
    {
        List<Map<String, Object>> signatureRows = materials.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryCode", item.get("categoryCode"));
            row.put("materialId", item.get("materialId"));
            row.put("versionNo", item.get("versionNo"));
            row.put("fileName", item.get("fileName"));
            row.put("fileSignature", item.get("fileSignature"));
            return row;
        }).toList();
        return JSON.toJSONString(signatureRows);
    }

    @SuppressWarnings("unchecked")
    private static String normalizeMaterialSignature(Object value)
    {
        if (value == null)
        {
            return "";
        }
        try
        {
            Object parsed = JSON.parse(String.valueOf(value));
            return parsed instanceof List<?> list
                    ? materialSignature((List<Map<String, Object>>) list) : "";
        }
        catch (Exception ex)
        {
            return "";
        }
    }

    private static Object parseValue(Object value, Object fallback)
    {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Map<?, ?> || value instanceof List<?> || value instanceof JSONObject
                || value instanceof JSONArray)
        {
            return value;
        }
        try
        {
            return JSON.parse(String.valueOf(value));
        }
        catch (Exception ex)
        {
            return fallback;
        }
    }

    private static Long numberValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            throw new ServiceException("审核版本数据异常");
        }
    }
}
