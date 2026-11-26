package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.domain.ExpertStatusRequest;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

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

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private ExpertObjectCleanupService objectCleanupService;

    @Autowired
    private ExpertQualificationReminderService qualificationReminderService;

    @Autowired
    private ExpertBusinessMapper businessMapper;

    @Autowired
    private ExpertCreditService creditService;

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
        profile.setCreditScore(100);
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

    public List<ExpertUnavailablePeriod> getUnavailablePeriods(Long userId)
    {
        return platformMapper.selectUnavailablePeriodsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOccupancyPeriods(Long userId,
            String serviceStart, String serviceEnd)
    {
        LocalDateTime start = parseDateTime(serviceStart);
        LocalDateTime end = parseDateTime(serviceEnd);
        if (start == null || end == null || !start.isBefore(end))
        {
            throw new ServiceException("任务占用查询时间范围不正确");
        }
        List<Map<String, Object>> snapshots = businessMapper.selectOccupancySnapshots(
                        userId,
                        start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .stream()
                .filter(item -> item.get("externalTaskId") instanceof String taskId
                        && StringUtils.isNotEmpty(taskId))
                .toList();
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
        return new java.util.ArrayList<>(latestByTask.values());
    }

    private static long contextId(Map<String, Object> snapshot)
    {
        Object value = snapshot.get("contextId");
        return value instanceof Number number ? number.longValue() : 0L;
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

    @Transactional
    public ExpertUnavailablePeriod saveUnavailablePeriod(ExpertUnavailablePeriod period,
            Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("请先保存专家档案，再提交临时不可用时段");
        }
        LocalDateTime start = parseDateTime(period.getStartTime());
        LocalDateTime end = parseDateTime(period.getEndTime());
        if (start == null || end == null || !start.isBefore(end))
        {
            throw new ServiceException("临时不可用结束时间必须晚于开始时间");
        }
        if (!end.isAfter(LocalDateTime.now()))
        {
            throw new ServiceException("临时不可用时段已经结束，请提交未来时段");
        }
        if (period.getReason() != null && period.getReason().length() > 500)
        {
            throw new ServiceException("不可用原因不能超过500个字符");
        }
        for (ExpertUnavailablePeriod existing :
                platformMapper.selectUnavailablePeriodsByUserId(userId))
        {
            if (period.getUnavailableId() != null
                    && period.getUnavailableId().equals(existing.getUnavailableId()))
            {
                continue;
            }
            LocalDateTime existingStart = parseDateTime(existing.getStartTime());
            LocalDateTime existingEnd = parseDateTime(existing.getEndTime());
            if (existingStart != null && existingEnd != null
                    && start.isBefore(existingEnd) && end.isAfter(existingStart))
            {
                throw new ServiceException("该时段与已有临时不可用时段重叠，请合并后提交");
            }
        }
        period.setUserId(userId);
        period.setStartTime(start.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        period.setEndTime(end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        period.setUpdateBy(username);
        if (period.getUnavailableId() == null)
        {
            period.setCreateBy(username);
            platformMapper.insertUnavailablePeriod(period);
        }
        else
        {
            if (platformMapper.updateUnavailablePeriod(period) == 0)
            {
                throw new ServiceException("临时不可用时段已被更新，请刷新后重试");
            }
        }
        return platformMapper.selectUnavailablePeriodById(period.getUnavailableId());
    }

    @Transactional
    public void deleteUnavailablePeriod(Long unavailableId, Long userId)
    {
        if (platformMapper.deleteUnavailablePeriod(unavailableId, userId) == 0)
        {
            throw new ServiceException("临时不可用时段不存在或无权操作");
        }
    }

    public List<ExpertMaterial> listMaterials(ExpertMaterial query, Long userId)
    {
        query.setUserId(userId);
        return platformMapper.selectMaterialList(query);
    }

    public List<ExpertMaterialFile> listMaterialFiles(Long materialId, Long userId)
    {
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null || !userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料文件不存在或无权操作");
        }
        List<ExpertMaterialFile> files = platformMapper.selectMaterialFiles(materialId);
        return files.isEmpty() && StringUtils.isNotEmpty(material.getFilePath())
                ? List.of(legacyFile(material)) : files;
    }

    public List<Map<String, Object>> qualificationReminders(Long userId)
    {
        return qualificationReminderService.reminders(userId);
    }

    @Transactional
    public ExpertMaterial updateMaterialValidity(Long materialId, ExpertMaterial request,
            Long userId, String username)
    {
        throw new ServiceException("审核有效期由系统在审核通过后自动计算，不能手动修改");
    }

    @Transactional
    public ExpertMaterialFile addMaterialFile(Long materialId, ExpertMaterialFile file,
            Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在或已停用");
        }
        ExpertMaterial material = platformMapper.selectMaterialByIdForUpdate(materialId);
        if (material == null || !userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料版本不存在或无权操作");
        }
        insertVersionFile(materialId, file, username);
        return platformMapper.selectMaterialFile(materialId, file.getFileId());
    }

    @Transactional
    public void deleteMaterialFile(Long materialId, Long fileId, Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在或已停用");
        }
        ExpertMaterial material = platformMapper.selectMaterialByIdForUpdate(materialId);
        if (material == null || !userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料版本不存在或无权操作");
        }
        ExpertMaterialFile file = platformMapper.selectMaterialFile(materialId, fileId);
        if (file == null)
        {
            throw new ServiceException("版本文件不存在");
        }
        if (platformMapper.countMaterialFiles(materialId) <= 1)
        {
            throw new ServiceException("当前版本至少需要保留一份文件");
        }
        if (platformMapper.deleteMaterialFile(materialId, fileId) == 0)
        {
            throw new ServiceException("版本文件已被删除，请刷新后重试");
        }
        if (file.getFilePath() != null && file.getFilePath().equals(material.getFilePath()))
        {
            List<ExpertMaterialFile> remaining = platformMapper.selectMaterialFiles(materialId);
            if (!remaining.isEmpty())
            {
                platformMapper.syncMaterialPrimaryFile(materialId, remaining.get(0), username);
            }
        }
        objectCleanupService.queue(file.getFilePath());
        objectCleanupService.drainAfterCommit();
    }

    @Transactional
    public ExpertMaterial submitMaterialRevision(Long materialId, Long userId, String username)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在或已停用");
        }
        ExpertMaterial material = platformMapper.selectMaterialByIdForUpdate(materialId);
        if (material == null || !userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料版本不存在或无权操作");
        }
        List<ExpertMaterialFile> files = platformMapper.selectMaterialFiles(materialId);
        if (files.isEmpty())
        {
            throw new ServiceException("当前版本没有可提交的文件");
        }
        platformMapper.syncMaterialPrimaryFile(materialId, files.get(0), username);
        submitMaterialForReview(material, userId, username);
        return platformMapper.selectMaterialById(materialId);
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
        Map<String, Object> angle = certificationAngle(userId, material.getMaterialCategory());
        if (angle == null)
        {
            throw new ServiceException("不支持的认证角度");
        }
        material.setValidFrom(null);
        material.setValidUntil(null);

        ExpertMaterial current = platformMapper.selectLatestMaterialByUserIdAndCategoryForUpdate(
                userId, material.getMaterialCategory());
        if (current == null)
        {
            if (material.getMaterialId() != null)
            {
                throw new ServiceException("原认证材料不存在，请刷新后重新上传");
            }
            material.setVersionNo(1);
            platformMapper.insertMaterial(material);
        }
        else
        {
            // 同一认证角度复用原版本记录，新上传内容作为版本内文件追加。
            material.setMaterialId(current.getMaterialId());
            material.setVersionNo(current.getVersionNo());
        }

        ExpertMaterialFile file = new ExpertMaterialFile();
        file.setMaterialId(material.getMaterialId());
        file.setFileName(material.getFileName());
        file.setFilePath(material.getFilePath());
        file.setFileSize(material.getFileSize());
        insertVersionFile(material.getMaterialId(), file, username);
        if (current == null)
        {
            platformMapper.syncMaterialPrimaryFile(material.getMaterialId(), file, username);
        }
        submitMaterialForReview(material, userId, username);
        return platformMapper.selectMaterialById(material.getMaterialId());
    }

    private void submitMaterialForReview(ExpertMaterial material, Long userId, String username)
    {
        platformMapper.prepareMaterialForReview(material.getMaterialId(), username);
        clearMaterialDerivedData(material.getMaterialId());
        String profileStatus = certificationService.profileStatusAfterReview(userId);
        platformMapper.updateProfileReviewStatus(userId, profileStatus);
        platformMapper.touchProfileSubmitTime(userId);
        certificationService.suspendServiceIfUncertified(userId, userId, username);
        refreshCompletion(userId);
        ingestService.enqueueAfterCommit(material.getMaterialId());
        graphOutboxService.enqueue("MATERIAL_SUBMITTED", "MATERIAL",
                material.getMaterialId(), Map.of(
                        "materialId", material.getMaterialId(),
                        "userId", userId,
                        "categoryCode", material.getMaterialCategory()));
    }

    private void insertVersionFile(Long materialId, ExpertMaterialFile file, String username)
    {
        boolean duplicated = platformMapper.selectMaterialFiles(materialId).stream()
                .anyMatch(item -> item.getFilePath() != null
                        && item.getFilePath().equals(file.getFilePath()));
        if (duplicated)
        {
            throw new ServiceException("该文件已存在于当前版本，请勿重复添加");
        }
        file.setMaterialId(materialId);
        file.setFileType(extractExtension(file.getFileName()));
        file.setSortNo(platformMapper.countMaterialFiles(materialId));
        file.setCreateBy(username);
        file.setUpdateBy(username);
        platformMapper.insertMaterialFile(file);
    }

    private static ExpertMaterialFile legacyFile(ExpertMaterial material)
    {
        ExpertMaterialFile file = new ExpertMaterialFile();
        file.setMaterialId(material.getMaterialId());
        file.setFileName(material.getFileName());
        file.setFilePath(material.getFilePath());
        file.setFileType(material.getFileType());
        file.setFileSize(material.getFileSize());
        file.setSortNo(0);
        file.setCreateTime(material.getCreateTime());
        file.setUpdateTime(material.getUpdateTime());
        return file;
    }

    private Map<String, Object> certificationAngle(Long userId, String categoryCode)
    {
        return platformMapper.selectCertificationStatus(userId).stream()
                .filter(item -> categoryCode.equals(item.get("categoryCode")))
                .findFirst()
                .orElse(null);
    }

    private void clearMaterialDerivedData(Long materialId)
    {
        // 原记录被新文件覆盖后，旧解析结果和旧图谱证据不能继续参与审核或画像。
        platformMapper.deleteFieldMappings(materialId);
        platformMapper.deleteDimensionAssessmentsByMaterial(materialId);
        platformMapper.deleteDimensionEvidenceByMaterial(materialId, "DRAFT");
        platformMapper.deleteDimensionEvidenceByMaterial(materialId, "APPROVED");
        graphMapper.deleteMaterialGraphRelations(materialId);
        graphMapper.deleteMaterialGraphEvidence(materialId);
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
        result.put("unavailablePeriodCount",
                platformMapper.selectUnavailablePeriodsByUserId(userId).size());
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
            result.put("message", "正式认证通过并生成有效能力画像后，才可查看画像");
            return result;
        }
        boolean published = profile.getGraphVersion() != null && profile.getGraphVersion() > 0;
        // DRAFT 是当前已审核材料重建出的有效画像；正式版本用于历史追溯。
        String graphStatus = "DRAFT";
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
        ExpertProfile statusProfile = getProfile(userId);
        result.put("profile", statusProfile);
        result.put("logs", platformMapper.selectStatusLogs(userId));
        result.put("pendingRequest", platformMapper.selectPendingStatusRequestByUserId(userId));
        result.put("requests", platformMapper.selectStatusRequestsByUserId(userId));
        result.put("certification", certificationService.status(userId));
        boolean creditBlocked = "CREDIT_BLOCKED".equals(statusProfile.getServiceStatus());
        result.put("isCreditBlocked", creditBlocked);
        result.put("creditThreshold", creditService.getBlockThreshold());
        result.put("creditLogs", platformMapper.selectCreditLogsByUserId(userId));
        result.put("canSubmitStatusChange", certificationService.isCertified(userId));
        result.put("canAcceptOrders", certificationService.isCertified(userId)
                && !creditBlocked);
        return result;
    }

    @Transactional
    public ExpertStatusRequest submitStatusChange(ExpertStatusChange change, Long userId, String username)
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
        if (change.getServiceStatus().equals(profile.getServiceStatus()))
        {
            throw new ServiceException("目标状态与当前接单状态相同，无需变更");
        }
        ExpertStatusRequest pending = platformMapper.selectPendingStatusRequestByUserId(userId);
        if ("CREDIT_BLOCKED".equals(profile.getServiceStatus()))
        {
            if (!"AVAILABLE".equals(change.getServiceStatus()))
            {
                throw new ServiceException("禁止接单状态下只能申请恢复为正常接单");
            }
            if (pending != null)
            {
                throw new ServiceException("已有待审核的状态申请，请等待审核或撤销后再提交");
            }
            ExpertStatusRequest request = new ExpertStatusRequest();
            request.setUserId(userId);
            request.setSourceStatus(profile.getServiceStatus());
            request.setTargetStatus("AVAILABLE");
            request.setReason(change.getReason().trim());
            request.setCreditRelated("1");
            request.setRequesterId(userId);
            request.setRequesterName(username);
            platformMapper.insertStatusRequest(request);
            return platformMapper.selectStatusRequestById(request.getRequestId());
        }
        if ("AVAILABLE".equals(change.getServiceStatus())
                || "PAUSED".equals(change.getServiceStatus()))
        {
            // 正常接单与暂停接单由专家直接切换；切换即表示放弃待审的状态申请。
            if (pending != null && platformMapper.cancelStatusRequest(pending.getRequestId(), userId,
                    pending.getVersion(), username, "专家切换接单状态，待审申请自动撤销") == 0)
            {
                throw new ServiceException("待审状态申请已发生变化，请刷新后重试");
            }
            String previous = profile.getServiceStatus();
            platformMapper.updateProfileServiceStatus(userId, change.getServiceStatus());
            platformMapper.insertStatusLog(userId, previous, change.getServiceStatus(),
                    change.getReason().trim(), change.isCreditRelated() ? "1" : "0",
                    userId, username);
            profile.setServiceStatus(change.getServiceStatus());
            platformMapper.syncApprovedProfile(profile);
            graphProjectionService.projectExpert(userId);
            graphOutboxService.enqueue("EXPERT_STATUS_CHANGED", "EXPERT", userId,
                    Map.of("userId", userId, "serviceStatus", change.getServiceStatus()));
            return null;
        }
        if (pending != null)
        {
            throw new ServiceException("已有待审核的状态申请，请先等待审核或撤销后再提交");
        }
        ExpertStatusRequest request = new ExpertStatusRequest();
        request.setUserId(userId);
        request.setSourceStatus(profile.getServiceStatus());
        request.setTargetStatus(change.getServiceStatus());
        request.setReason(change.getReason().trim());
        request.setCreditRelated(change.isCreditRelated() ? "1" : "0");
        request.setRequesterId(userId);
        request.setRequesterName(username);
        platformMapper.insertStatusRequest(request);
        return platformMapper.selectStatusRequestById(request.getRequestId());
    }

    @Transactional
    public ExpertStatusRequest cancelStatusRequest(Long requestId, Integer version, Long userId,
            String username)
    {
        ExpertStatusRequest overview = platformMapper.selectStatusRequestById(requestId);
        if (overview == null || !userId.equals(overview.getUserId()))
        {
            throw new ServiceException("专家状态申请不存在");
        }
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertStatusRequest request = platformMapper.selectStatusRequestByIdForUpdate(requestId);
        if (request == null || !userId.equals(request.getUserId()))
        {
            throw new ServiceException("专家状态申请不存在");
        }
        if (!"PENDING".equals(request.getRequestStatus()))
        {
            throw new ServiceException("该专家状态申请已经处理，不能撤销");
        }
        int expectedVersion = version == null ? request.getVersion() : version;
        if (!Integer.valueOf(expectedVersion).equals(request.getVersion()))
        {
            throw new ServiceException("专家状态申请已更新，请刷新后重试");
        }
        if (platformMapper.cancelStatusRequest(requestId, userId, expectedVersion, username,
                "专家撤销状态申请") == 0)
        {
            throw new ServiceException("专家状态申请已处理，不能撤销");
        }
        return platformMapper.selectStatusRequestById(requestId);
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
                return LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern(pattern));
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种允许的输入格式。
            }
        }
        return null;
    }
}
