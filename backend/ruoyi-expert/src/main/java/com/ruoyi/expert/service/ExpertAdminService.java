package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertAdminSaveRequest;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 管理员专家总体情况维护。
 */
@Service
public class ExpertAdminService
{
    private static final Set<String> SERVICE_STATUSES =
            Set.of("AVAILABLE", "PAUSED", "PENDING_CERTIFICATION",
                    "CREDIT_BLOCKED", "EXITED");

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertWorkspaceService workspaceService;

    @Autowired
    private ExpertCertificationService certificationService;

    @Autowired
    private ExpertCreditService creditService;

    @Autowired
    private ExpertIngestService ingestService;

    @Autowired
    private ExpertObjectCleanupService cleanupService;

    @Autowired
    private KnowledgeGraphProjectionService graphProjectionService;

    @Autowired
    private KnowledgeGraphOutboxService graphOutboxService;

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    @Autowired
    private ExpertVersionService versionService;

    public List<Map<String, Object>> list(Map<String, Object> query)
    {
        return platformMapper.selectAdminExpertList(query);
    }

    public List<Map<String, Object>> expertUsers()
    {
        return platformMapper.selectExpertRoleUsers();
    }

    public void ensureExpertUser(Long userId)
    {
        if (userId == null || platformMapper.countExpertRoleUser(userId) == 0)
        {
            throw new ServiceException("目标账号不存在或尚未分配专家角色");
        }
    }

    public Map<String, Object> certifications(Long userId)
    {
        ensureExpertUser(userId);
        return certificationService.status(userId);
    }

    public List<ExpertSchedule> schedules(Long userId)
    {
        ensureExpertUser(userId);
        return platformMapper.selectScheduleByUserId(userId);
    }

    public void saveSchedules(Long userId, List<ExpertSchedule> schedules, String operatorName)
    {
        ensureExpertUser(userId);
        workspaceService.saveSchedules(schedules, userId, operatorName);
    }

    public List<ExpertUnavailablePeriod> unavailablePeriods(Long userId)
    {
        ensureExpertUser(userId);
        return platformMapper.selectUnavailablePeriodsByUserId(userId);
    }

    public List<Map<String, Object>> profileVersions(Long userId)
    {
        ensureExpertUser(userId);
        return versionService.timeline(userId);
    }

    public Map<String, Object> profileVersion(Long userId, Long releaseId)
    {
        ensureExpertUser(userId);
        return versionService.detail(userId, releaseId);
    }

    public Map<String, Object> graph(Long userId)
    {
        ensureExpertUser(userId);
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            return unavailableGraph("专家尚未建立档案，无法查看画像");
        }
        boolean published = "APPROVED".equals(profile.getReviewStatus())
                && profile.getGraphVersion() != null && profile.getGraphVersion() > 0;
        // 当前视图读取最新已审核材料重建的有效画像，历史正式版本由快照保存。
        String scoreStatus = "DRAFT";
        List<ExpertGraphDimension> allDimensions =
                dimensionViewService.dimensions(userId, scoreStatus);
        List<ExpertGraphDimension> available = allDimensions.stream()
                .filter(item -> "AVAILABLE".equals(item.getDataStatus())).toList();
        List<ExpertGraphDimension> conflicts = allDimensions.stream()
                .filter(item -> "CONFLICT".equals(item.getDataStatus())).toList();
        long evidenceCount = allDimensions.stream()
                .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus())).count();
        int total = allDimensions.size();
        Map<String, Object> result = new HashMap<>();
        String status = evidenceCount == 0 ? "PENDING"
                : published && available.size() == total && conflicts.isEmpty()
                        ? "APPROVED" : "PARTIAL";
        result.put("status", status);
        result.put("statusText", "APPROVED".equals(status) ? "正式能力画像"
                : "PARTIAL".equals(status) ? "部分能力画像" : "画像待生成");
        result.put("graphVersion", profile.getGraphVersion());
        result.put("dimensions", allDimensions);
        result.put("totalDimensionCount", total);
        result.put("availableDimensionCount", available.size());
        result.put("conflictCount", conflicts.size());
        result.put("unknownCount", Math.max(0, total - (int) evidenceCount));
        result.put("coverage", total == 0 ? 0
                : Math.round(evidenceCount * 100.0 / total));
        result.put("profileKind", evidenceCount == 0 ? "UNKNOWN"
                : commonProfileKind(allDimensions.stream()
                        .filter(item -> !"INSUFFICIENT_EVIDENCE".equals(item.getDataStatus()))
                        .toList()));
        result.put("message", switch (status)
        {
            case "APPROVED" -> "专家已正式认证，当前展示完整能力画像";
            case "PARTIAL" -> "当前有 " + evidenceCount + "/" + total
                    + " 个维度具备证据，其中 " + conflicts.size()
                    + " 个维度存在冲突；其余维度显示为证据不足";
            default -> "档案和图谱已建立，但尚未生成有证据的能力维度；可先查看知识图谱和材料证据";
        });
        return result;
    }

    /**
     * 兼容旧版调用；新版管理端应使用 ExpertAdminSaveRequest 同时提交认证材料。
     */
    @Deprecated
    @Transactional
    public ExpertProfile save(ExpertProfile profile, Long userId, Long operatorId, String operatorName)
    {
        ExpertAdminSaveRequest request = new ExpertAdminSaveRequest();
        request.setProfile(profile);
        return save(request, userId, operatorId, operatorName);
    }

    @Transactional
    public ExpertProfile save(ExpertAdminSaveRequest request, Long userId, Long operatorId, String operatorName)
    {
        ensureExpertUser(userId);
        ExpertProfile profile = request.getProfile();
        if (profile == null)
        {
            throw new ServiceException("专家档案信息不能为空");
        }
        List<ExpertMaterial> uploads = request.getMaterials() == null
                ? new ArrayList<>() : request.getMaterials();
        // 与审核员、专家重新提交共用档案行锁，避免并发操作互相覆盖。
        ExpertProfile existing = platformMapper.selectAdminProfileByUserIdForUpdate(userId);
        boolean restoring = existing != null && "2".equals(existing.getDelFlag());
        if (existing != null && !restoring && !java.util.Objects.equals(
                profile.getVersion() == null ? 0 : profile.getVersion(),
                existing.getVersion() == null ? 0 : existing.getVersion()))
        {
            throw new ServiceException("专家档案已被其他操作更新，请刷新后重新编辑");
        }
        Map<String, Object> certificationBefore = certificationService.status(userId);
        Map<String, Map<String, Object>> anglesByCode = new HashMap<>();
        Set<String> approvedCategories = new HashSet<>();
        for (Map<String, Object> angle : angleRows(certificationBefore))
        {
            String categoryCode = String.valueOf(angle.get("categoryCode"));
            anglesByCode.put(categoryCode, angle);
            if ("APPROVED".equals(String.valueOf(angle.get("reviewStatus"))))
            {
                approvedCategories.add(categoryCode);
            }
        }

        Set<String> uploadCategories = new HashSet<>();
        for (ExpertMaterial material : uploads)
        {
            String categoryCode = material.getMaterialCategory();
            Map<String, Object> angle = anglesByCode.get(categoryCode);
            if (angle == null)
            {
                throw new ServiceException("不支持的认证角度：" + categoryCode);
            }
            if (!uploadCategories.add(categoryCode))
            {
                throw new ServiceException("同一认证角度一次只能上传一份最新材料");
            }
            if (StringUtils.isEmpty(material.getFilePath())
                    || !material.getFilePath().startsWith("minio://")
                    || !material.getFilePath().contains("/experts/" + userId + "/"))
            {
                throw new ServiceException("认证材料文件地址无效，请重新上传");
            }
            material.setValidFrom(null);
            material.setValidUntil(null);
        }

        List<String> missingAngles = new ArrayList<>();
        for (Map<String, Object> angle : angleRows(certificationBefore))
        {
            String categoryCode = String.valueOf(angle.get("categoryCode"));
            boolean required = "1".equals(String.valueOf(angle.get("requiredFlag")));
            if (required && !approvedCategories.contains(categoryCode)
                    && !uploadCategories.contains(categoryCode))
            {
                missingAngles.add(String.valueOf(angle.get("angleName")));
            }
        }
        if (!missingAngles.isEmpty())
        {
            throw new ServiceException("认证材料不完整，请上传：" + String.join("、", missingAngles));
        }

        boolean activeProfile = existing != null && !"2".equals(existing.getDelFlag());
        boolean wasCertified = activeProfile && certificationService.isCertified(userId);
        String previousServiceStatus = existing == null ? null : existing.getServiceStatus();
        String serviceStatus = profile.getServiceStatus();
        if (!wasCertified)
        {
            serviceStatus = "PENDING_CERTIFICATION";
        }
        else if (StringUtils.isEmpty(serviceStatus))
        {
            serviceStatus = existing.getServiceStatus();
        }
        if (wasCertified && creditService.isBelowThreshold(userId)
                && !"EXITED".equals(serviceStatus))
        {
            serviceStatus = "CREDIT_BLOCKED";
        }
        if (!SERVICE_STATUSES.contains(serviceStatus))
        {
            throw new ServiceException("不支持的接单状态");
        }

        profile.setUserId(userId);
        profile.setServiceStatus(serviceStatus);
        profile.setReviewStatus(activeProfile ? existing.getReviewStatus() : "DRAFT");
        profile.setProfileCompletion(activeProfile ? existing.getProfileCompletion() : 0);
        profile.setGraphVersion(activeProfile ? existing.getGraphVersion() : 0);
        profile.setLatestSubmitTime(activeProfile ? existing.getLatestSubmitTime() : null);
        profile.setApprovedTime(activeProfile ? existing.getApprovedTime() : null);
        profile.setCreateBy(existing == null ? operatorName : existing.getCreateBy());
        profile.setUpdateBy(operatorName);
        profile.setDelFlag("0");

        platformMapper.upsertAdminProfile(profile);
        Long sourceMaterialId = null;
        List<Long> materialIds = new ArrayList<>();
        for (ExpertMaterial material : uploads)
        {
            String categoryCode = material.getMaterialCategory();
            int versionNo = platformMapper.selectMaxMaterialVersion(userId, categoryCode) + 1;
            material.setUserId(userId);
            material.setVersionNo(versionNo);
            material.setFileType(extractExtension(material.getFileName()));
            material.setCreateBy(operatorName);
            material.setUpdateBy(operatorName);
            if ("1".equals(String.valueOf(anglesByCode.get(categoryCode)
                    .get("validityRequired"))))
            {
                LocalDate approvedDate = LocalDate.now();
                material.setValidFrom(approvedDate.toString());
                material.setValidUntil(approvedDate.plusMonths(3).toString());
            }
            platformMapper.insertAdminApprovedMaterial(material);
            ExpertMaterialFile file = new ExpertMaterialFile();
            file.setMaterialId(material.getMaterialId());
            file.setFileName(material.getFileName());
            file.setFilePath(material.getFilePath());
            file.setFileType(material.getFileType());
            file.setFileSize(material.getFileSize());
            file.setSortNo(0);
            file.setCreateBy(operatorName);
            file.setUpdateBy(operatorName);
            platformMapper.insertMaterialFile(file);
            materialIds.add(material.getMaterialId());
            sourceMaterialId = material.getMaterialId();
            Object previousStatusValue = anglesByCode.get(categoryCode).get("reviewStatus");
            String previousAngleStatus = previousStatusValue == null
                    ? "NOT_UPLOADED" : String.valueOf(previousStatusValue);
            platformMapper.insertReviewLog(userId, material.getMaterialId(), "ADMIN_APPROVE",
                    operatorId, operatorName, previousAngleStatus, "APPROVED",
                    "管理员建档上传认证材料，免人工审核");
        }

        Map<String, Object> certificationAfter = certificationService.status(userId);
        if (!Boolean.TRUE.equals(certificationAfter.get("fullyCertified")))
        {
            throw new ServiceException("认证材料尚未覆盖全部必选角度，专家档案暂不能认证");
        }
        platformMapper.updateProfileReviewStatus(userId, "APPROVED");
        certificationService.enableServiceAfterCertification(userId, operatorId, operatorName);
        platformMapper.touchProfileSubmitTime(userId);
        workspaceService.refreshCompletion(userId);
        ExpertProfile saved = platformMapper.selectAdminProfileByUserId(userId);

        if (sourceMaterialId == null)
        {
            ExpertMaterial latest = platformMapper.selectLatestMaterialByUserId(userId);
            sourceMaterialId = latest == null ? null : latest.getMaterialId();
        }
        platformMapper.upsertApprovedExpert(saved, sourceMaterialId, operatorName,
                JSON.toJSONString(saved));
        graphProjectionService.projectExpert(userId);
        graphOutboxService.enqueue("PROFILE_UPDATED", "EXPERT", userId,
                Map.of("userId", userId, "operatorId", operatorId));
        if (previousServiceStatus != null && !previousServiceStatus.equals(serviceStatus))
        {
            // 管理员直接调整状态时，同一事务内作废未处理申请，防止审核员随后覆盖管理员结果。
            platformMapper.cancelPendingStatusRequests(userId, operatorName,
                    "管理员直接调整接单状态，原申请自动作废");
            String reason = restoring ? "管理员恢复专家档案" : "管理员维护专家总体情况";
            platformMapper.insertStatusLog(userId, previousServiceStatus, serviceStatus, reason,
                    "0", operatorId, operatorName);
        }
        for (Long materialId : materialIds)
        {
            graphOutboxService.enqueue("MATERIAL_SUBMITTED", "MATERIAL", materialId,
                    Map.of("materialId", materialId, "userId", userId));
            ingestService.enqueueAfterCommit(materialId);
        }
        return saved;
    }

    @Transactional
    public void delete(Long userId, Long operatorId, String operatorName)
    {
        ensureExpertUser(userId);
        ExpertProfile existing = platformMapper.selectAdminProfileByUserIdForUpdate(userId);
        if (existing == null || "2".equals(existing.getDelFlag()))
        {
            throw new ServiceException("专家档案不存在或已经删除");
        }
        ExpertMaterial query = new ExpertMaterial();
        query.setUserId(userId);
        List<ExpertMaterial> materials = platformMapper.selectMaterialList(query);
        Set<String> objectUris = new HashSet<>();
        for (ExpertMaterial material : materials)
        {
            if (StringUtils.isNotEmpty(material.getFilePath()))
            {
                objectUris.add(material.getFilePath());
            }
            platformMapper.selectMaterialFiles(material.getMaterialId()).stream()
                    .map(ExpertMaterialFile::getFilePath)
                    .filter(StringUtils::isNotEmpty)
                    .forEach(objectUris::add);
        }

        graphOutboxService.enqueue("EXPERT_DELETED", "EXPERT", userId,
                Map.of("userId", userId, "operatorId", operatorId));
        graphMapper.deleteMaterialFactsByUserId(userId);
        graphMapper.deleteMaterialEvidenceByUserId(userId);
        graphProjectionService.deleteExpertProjection(userId);
        platformMapper.deleteFieldMappingsByUserId(userId);
        platformMapper.deleteMaterialsByUserId(userId);
        platformMapper.deleteReviewLogsByUserId(userId);
        platformMapper.deleteStatusRequestsByUserId(userId);
        platformMapper.deleteStatusLogsByUserId(userId);
        platformMapper.deleteGraphScoresByUserId(userId);
        platformMapper.deleteModelCallLogsByUserId(userId);
        platformMapper.deleteScheduleByUserId(userId);
        platformMapper.deleteApprovedExpert(userId);
        if (platformMapper.deleteExpertProfile(userId) == 0)
        {
            throw new ServiceException("专家档案清空失败，请刷新后重试");
        }

        for (String objectUri : objectUris)
        {
            cleanupService.queue(objectUri);
        }
        cleanupService.drainAfterCommit();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> angleRows(Map<String, Object> certification)
    {
        Object angles = certification.get("angles");
        return angles instanceof List ? (List<Map<String, Object>>) angles : List.of();
    }

    private static String extractExtension(String fileName)
    {
        if (fileName == null || !fileName.contains("."))
        {
            return "unknown";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String commonProfileKind(List<ExpertGraphDimension> dimensions)
    {
        java.util.Set<String> kinds = dimensions.stream()
                .map(ExpertGraphDimension::getProfileKind)
                .filter(kind -> kind != null && !kind.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return kinds.size() == 1 ? kinds.iterator().next() : "MIXED";
    }

    private static Map<String, Object> unavailableGraph(String message)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "UNAVAILABLE");
        result.put("statusText", "暂无画像");
        result.put("graphVersion", 0);
        result.put("dimensions", List.of());
        result.put("message", message);
        return result;
    }
}
