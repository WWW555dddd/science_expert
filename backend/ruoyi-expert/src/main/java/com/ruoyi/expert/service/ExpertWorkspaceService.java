package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 专家工作台业务。
 */
@Service
public class ExpertWorkspaceService
{
    private static final Set<String> SERVICE_STATUSES = Set.of("AVAILABLE", "PAUSED", "EXITED");

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertIngestService ingestService;

    @Autowired
    private ExpertCertificationService certificationService;

    @Autowired
    private KnowledgeGraphProjectionService graphProjectionService;

    @Autowired
    private KnowledgeGraphOutboxService graphOutboxService;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    public ExpertProfile getProfile(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile != null)
        {
            return profile;
        }
        if (platformMapper.countDeletedProfileByUserId(userId) > 0)
        {
            throw new ServiceException("专家档案已由管理员停用，请联系平台管理员恢复");
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setUserName(loginUser.getUsername());
        profile.setNickName(loginUser.getUser().getNickName());
        profile.setRealName(loginUser.getUser().getNickName());
        profile.setReviewStatus("DRAFT");
        profile.setServiceStatus("PENDING_CERTIFICATION");
        profile.setProfileCompletion(0);
        profile.setGraphVersion(0);
        profile.setMaterialCount(0);
        return profile;
    }

    @Transactional
    public ExpertProfile saveProfile(ExpertProfile profile, Long userId, String username)
    {
        if (platformMapper.countDeletedProfileByUserId(userId) > 0)
        {
            throw new ServiceException("专家档案已由管理员停用，请联系平台管理员恢复");
        }
        ExpertProfile existing = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (existing != null && !java.util.Objects.equals(
                profile.getVersion() == null ? 0 : profile.getVersion(),
                existing.getVersion() == null ? 0 : existing.getVersion()))
        {
            throw new ServiceException("专家档案已被其他操作更新，请刷新后重新编辑");
        }
        profile.setUserId(userId);
        profile.setCreateBy(username);
        profile.setUpdateBy(username);
        profile.setProfileCompletion(calculateCompletion(profile));
        platformMapper.upsertProfile(profile);
        platformMapper.updateProfileReviewStatus(userId, certificationService.profileStatusAfterReview(userId));
        ExpertProfile saved = getProfile(userId);
        platformMapper.syncApprovedProfile(saved);
        graphProjectionService.projectExpert(userId);
        graphOutboxService.enqueue("PROFILE_UPDATED", "EXPERT", userId,
                Map.of("userId", userId));
        return saved;
    }

    public List<ExpertSchedule> getSchedules(Long userId)
    {
        return platformMapper.selectScheduleByUserId(userId);
    }

    @Transactional
    public void saveSchedules(List<ExpertSchedule> schedules, Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("请先保存专家档案，再设置可服务时间");
        }
        if (schedules == null || schedules.isEmpty())
        {
            throw new ServiceException("请至少设置一个可服务时间段");
        }
        for (ExpertSchedule schedule : schedules)
        {
            if (schedule.getWeekday() == null || schedule.getWeekday() < 1 || schedule.getWeekday() > 7)
            {
                throw new ServiceException("星期必须在1到7之间");
            }
            if (StringUtils.isEmpty(schedule.getStartTime()) || StringUtils.isEmpty(schedule.getEndTime())
                    || schedule.getStartTime().compareTo(schedule.getEndTime()) >= 0)
            {
                throw new ServiceException("可服务时间段的结束时间必须晚于开始时间");
            }
            schedule.setUserId(userId);
            schedule.setCreateBy(username);
            schedule.setUpdateBy(username);
            if (StringUtils.isEmpty(schedule.getServiceMode()))
            {
                schedule.setServiceMode("REMOTE");
            }
            if (schedule.getWeeklyCapacity() == null || schedule.getWeeklyCapacity() < 1)
            {
                schedule.setWeeklyCapacity(1);
            }
        }
        platformMapper.deleteScheduleByUserId(userId);
        for (ExpertSchedule schedule : schedules)
        {
            platformMapper.insertSchedule(schedule);
        }
        platformMapper.updateProfileReviewStatus(userId, certificationService.profileStatusAfterReview(userId));
        refreshCompletion(userId);
    }

    public List<ExpertMaterial> listMaterials(ExpertMaterial query, Long userId)
    {
        query.setUserId(userId);
        return platformMapper.selectMaterialList(query);
    }

    @Transactional
    public ExpertMaterial submitMaterial(ExpertMaterial material, Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("请先完善专家档案并设置可服务时间");
        }
        material.setUserId(userId);
        material.setCreateBy(username);
        material.setUpdateBy(username);
        material.setFileType(extractExtension(material.getFileName()));
        if (StringUtils.isEmpty(material.getMaterialCategory()))
        {
            material.setMaterialCategory("OTHER");
        }
        boolean validCategory = platformMapper.selectCertificationStatus(userId).stream()
                .anyMatch(item -> material.getMaterialCategory().equals(item.get("categoryCode")));
        if (!validCategory)
        {
            throw new ServiceException("不支持的认证角度");
        }
        int latestVersion = platformMapper.selectMaxMaterialVersion(userId, material.getMaterialCategory());
        material.setVersionNo(latestVersion + 1);
        platformMapper.insertMaterial(material);
        platformMapper.updateProfileReviewStatus(userId, "PENDING_REVIEW");
        platformMapper.touchProfileSubmitTime(userId);
        certificationService.suspendServiceIfUncertified(userId, userId, username);
        refreshCompletion(userId);
        ingestService.enqueueAfterCommit(material.getMaterialId());
        graphOutboxService.enqueue("MATERIAL_SUBMITTED", "MATERIAL",
                material.getMaterialId(), Map.of(
                        "materialId", material.getMaterialId(),
                        "userId", userId,
                        "categoryCode", material.getMaterialCategory()));
        return platformMapper.selectMaterialById(material.getMaterialId());
    }

    @Transactional
    public void retryMaterial(Long materialId, Long userId)
    {
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null || !userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料文件不存在或无权操作");
        }
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在或已停用");
        }
        if (platformMapper.resetMaterialJob(materialId) == 0)
        {
            throw new ServiceException("资料正在处理或已经排队，请稍后刷新");
        }
        ingestService.enqueueAfterCommit(materialId);
    }

    public Map<String, Object> summary(Long userId)
    {
        ExpertProfile profile = getProfile(userId);
        List<ExpertSchedule> schedules = platformMapper.selectScheduleByUserId(userId);
        List<ExpertMaterial> materials = platformMapper.selectMaterialList(new ExpertMaterial()
        {{
            setUserId(userId);
        }});
        long processing = materials.stream()
                .filter(item -> !"REVIEW_PENDING".equals(item.getJobStatus())
                        && !"PRE_REVIEW_PASSED".equals(item.getJobStatus())
                        && !"FAILED".equals(item.getJobStatus()))
                .count();
        long pendingReview = materials.stream()
                .filter(item -> "REVIEW_PENDING".equals(item.getJobStatus())
                        || "PRE_REVIEW_PASSED".equals(item.getJobStatus()))
                .count();
        Map<String, Object> result = new HashMap<>();
        result.put("profile", profile);
        result.put("scheduleCount", schedules.size());
        result.put("materialCount", materials.size());
        result.put("processingCount", processing);
        result.put("pendingReviewCount", pendingReview);
        result.put("latestMaterial", materials.isEmpty() ? null : materials.get(0));
        result.put("certification", certificationService.status(userId));
        return result;
    }

    public Map<String, Object> certifications(Long userId)
    {
        return certificationService.status(userId);
    }

    public Map<String, Object> graph(Long userId)
    {
        ExpertProfile profile = getProfile(userId);
        Map<String, Object> result = new HashMap<>();
        if (!"APPROVED".equals(profile.getReviewStatus()))
        {
            result.put("status", "UNAVAILABLE");
            result.put("graphVersion", 0);
            result.put("dimensions", List.of());
            result.put("message", "正式认证通过并生成完整能力画像后，才可查看画像");
            return result;
        }
        boolean published = profile.getGraphVersion() != null && profile.getGraphVersion() > 0;
        String graphStatus = published ? "APPROVED" : "DRAFT";
        List<ExpertGraphDimension> allDimensions =
                dimensionViewService.dimensions(userId, graphStatus);
        List<ExpertGraphDimension> available = allDimensions.stream()
                .filter(item -> "AVAILABLE".equals(item.getDataStatus())).toList();
        long conflicts = allDimensions.stream()
                .filter(item -> "CONFLICT".equals(item.getDataStatus())).count();
        long evidenceCount = allDimensions.stream()
                .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus())).count();
        String status = evidenceCount == 0 ? "PENDING"
                : published && available.size() == allDimensions.size() && conflicts == 0
                        ? "APPROVED" : "PARTIAL";
        result.put("status", status);
        result.put("graphVersion", profile.getGraphVersion());
        result.put("dimensions", allDimensions);
        result.put("totalDimensionCount", allDimensions.size());
        result.put("availableDimensionCount", available.size());
        result.put("conflictCount", conflicts);
        result.put("unknownCount", allDimensions.size() - evidenceCount);
        result.put("coverage", allDimensions.isEmpty() ? 0
                : Math.round(evidenceCount * 100.0 / allDimensions.size()));
        result.put("profileKind", evidenceCount == 0 ? "UNKNOWN"
                : commonProfileKind(allDimensions.stream()
                        .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus()))
                        .toList()));
        result.put("message", switch (status)
        {
            case "APPROVED" -> "正式认证已通过，当前展示完整能力画像";
            case "PARTIAL" -> "当前有 " + evidenceCount + "/" + allDimensions.size()
                    + " 个维度具备证据，其中 " + conflicts
                    + " 个维度存在冲突；其余维度显示为证据不足";
            default -> "档案已认证，但尚未生成有证据的能力维度；请查看知识图谱或重新处理材料";
        });
        return result;
    }

    private static String commonProfileKind(List<ExpertGraphDimension> dimensions)
    {
        Set<String> kinds = dimensions.stream()
                .map(ExpertGraphDimension::getProfileKind)
                .filter(StringUtils::isNotEmpty)
                .collect(java.util.stream.Collectors.toSet());
        return kinds.size() == 1 ? kinds.iterator().next() : "MIXED";
    }

    @Transactional(readOnly = true)
    public Map<String, Object> statusSnapshot(Long userId)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("profile", getProfile(userId));
        result.put("logs", platformMapper.selectStatusLogs(userId));
        result.put("certification", certificationService.status(userId));
        result.put("canAcceptOrders", certificationService.isCertified(userId));
        return result;
    }

    @Transactional
    public void changeServiceStatus(ExpertStatusChange change, Long userId, String username)
    {
        if (!SERVICE_STATUSES.contains(change.getServiceStatus()))
        {
            throw new ServiceException("不支持的接单状态");
        }
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        if (!certificationService.isCertified(userId))
        {
            throw new ServiceException("专家尚未正式认证通过，接单状态锁定为待认证，不能选择其他状态");
        }
        LoginUser loginUser = SecurityUtils.getLoginUser();
        boolean privileged = loginUser.getPermissions().contains("*:*:*")
                || loginUser.getPermissions().contains("review:action")
                || loginUser.getPermissions().contains("expert:admin:view");
        if (!privileged && (change.isCreditRelated()
                || "EXITED".equals(change.getServiceStatus())))
        {
            throw new ServiceException("信用限制和退出状态需要审核人员或管理人员处理");
        }
        String previous = profile.getServiceStatus();
        platformMapper.updateProfileServiceStatus(userId, change.getServiceStatus());
        platformMapper.insertStatusLog(userId, previous, change.getServiceStatus(), change.getReason(),
                change.isCreditRelated() ? "1" : "0", loginUser.getUserId(), username);
        profile.setServiceStatus(change.getServiceStatus());
        platformMapper.syncApprovedProfile(profile);
        graphProjectionService.projectExpert(userId);
        graphOutboxService.enqueue("EXPERT_STATUS_CHANGED", "EXPERT", userId,
                Map.of("userId", userId, "serviceStatus", change.getServiceStatus()));
    }

    public void refreshCompletion(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile != null)
        {
            platformMapper.updateProfileCompletion(userId, calculateCompletion(profile));
        }
    }

    private int calculateCompletion(ExpertProfile profile)
    {
        int completed = 0;
        completed += hasText(profile.getRealName()) ? 1 : 0;
        completed += hasText(profile.getOrganization()) ? 1 : 0;
        completed += hasText(profile.getProfessionalTitle()) ? 1 : 0;
        completed += hasText(profile.getResearchField()) ? 1 : 0;
        completed += hasText(profile.getIndustryField()) ? 1 : 0;
        completed += hasText(profile.getServiceRegion()) ? 1 : 0;
        completed += hasText(profile.getPhone()) ? 1 : 0;
        completed += hasText(profile.getEmail()) ? 1 : 0;
        completed += hasText(profile.getCareerSummary()) ? 1 : 0;
        completed += platformMapper.selectScheduleByUserId(profile.getUserId()).isEmpty() ? 0 : 1;
        ExpertMaterial query = new ExpertMaterial();
        query.setUserId(profile.getUserId());
        completed += platformMapper.selectMaterialList(query).isEmpty() ? 0 : 1;
        return Math.min(100, (int) Math.round(completed * 100.0 / 11));
    }

    private static boolean hasText(String value)
    {
        return StringUtils.isNotEmpty(value);
    }

    private static String extractExtension(String fileName)
    {
        if (fileName == null || !fileName.contains("."))
        {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }
}
