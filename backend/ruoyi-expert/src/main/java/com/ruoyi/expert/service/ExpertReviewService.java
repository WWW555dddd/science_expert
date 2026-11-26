package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertAiResultEditRequest;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertReviewAction;
import com.ruoyi.expert.domain.ExpertStatusRequest;
import com.ruoyi.expert.domain.ExpertStatusReviewAction;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 专家人工审核业务。
 */
@Service
public class ExpertReviewService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertIngestService ingestService;

    @Autowired
    private ExpertCertificationService certificationService;

    @Autowired
    private ExpertGraphService graphService;

    @Autowired
    private ExpertProfileDimensionAggregationService dimensionAggregationService;

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

    @Autowired
    private ExpertCreditService creditService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> queue(Map<String, Object> query)
    {
        List<Map<String, Object>> rows = platformMapper.selectReviewQueue(query);
        for (Map<String, Object> row : rows)
        {
            Object userId = row.get("userId");
            if (userId instanceof Number)
            {
                row.put("certification", certificationService.status(((Number) userId).longValue()));
            }
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long userId)
    {
        Map<String, Object> detail = platformMapper.selectReviewDetail(userId);
        if (detail == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertMaterial latest = platformMapper.selectLatestMaterialByUserId(userId);
        Long materialId = latest == null ? null : latest.getMaterialId();
        Map<String, Object> result = new HashMap<>();
        result.put("profile", detail);
        result.put("schedules", platformMapper.selectScheduleByUserId(userId));
        result.put("unavailablePeriods",
                platformMapper.selectUnavailablePeriodsByUserId(userId));
        List<ExpertMaterial> materials = platformMapper.selectMaterialList(new ExpertMaterial()
        {{
            setUserId(userId);
        }});
        result.put("materials", materials);
        result.put("mappings", materialId == null ? List.of() : platformMapper.selectFieldMappings(materialId));
        Map<Long, List<Map<String, Object>>> mappingsByMaterial = new HashMap<>();
        Map<Long, List<ExpertMaterialFile>> filesByMaterial =
                new HashMap<>();
        Map<Long, List<Map<String, Object>>> dimensionsByMaterial = new HashMap<>();
        Map<Long, List<Map<String, Object>>> factsByMaterial = new HashMap<>();
        for (ExpertMaterial material : materials)
        {
            mappingsByMaterial.put(material.getMaterialId(),
                    platformMapper.selectFieldMappings(material.getMaterialId()));
            filesByMaterial.put(material.getMaterialId(),
                    platformMapper.selectMaterialFiles(material.getMaterialId()));
            dimensionsByMaterial.put(material.getMaterialId(),
                    platformMapper.selectMaterialDimensionAssessments(material.getMaterialId()));
            factsByMaterial.put(material.getMaterialId(),
                    graphMapper.selectMaterialReviewFacts(material.getMaterialId()));
        }
        result.put("mappingsByMaterial", mappingsByMaterial);
        result.put("filesByMaterial", filesByMaterial);
        result.put("dimensionsByMaterial", dimensionsByMaterial);
        result.put("factsByMaterial", factsByMaterial);
        result.put("certification", certificationService.status(userId));
        result.put("reviewLogs", platformMapper.selectReviewLogs(userId));
        result.put("statusLogs", platformMapper.selectStatusLogs(userId));
        result.put("graph", dimensionViewService.dimensions(userId, "DRAFT"));
        return result;
    }

    @Transactional
    public void action(Long userId, ExpertReviewAction action, LoginUser reviewer)
    {
        // 锁定同一专家档案，使多个审核员、管理员直审和人工审核串行执行。
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertMaterial material = action.getMaterialId() == null
                ? platformMapper.selectLatestMaterialByUserId(userId)
                : platformMapper.selectMaterialById(action.getMaterialId());
        if (material == null)
        {
            throw new ServiceException("没有可审核的资料文件");
        }
        if (!userId.equals(material.getUserId()))
        {
            throw new ServiceException("资料文件不属于该专家");
        }
        ExpertMaterial materialQuery = new ExpertMaterial();
        materialQuery.setUserId(userId);
        boolean latestVersion = platformMapper.selectMaterialList(materialQuery).stream()
                .filter(item -> material.getMaterialCategory().equals(item.getMaterialCategory()))
                .findFirst()
                .map(item -> material.getMaterialId().equals(item.getMaterialId()))
                .orElse(false);
        if (!latestVersion)
        {
            throw new ServiceException("仅允许审核该认证角度的最新材料版本");
        }
        boolean materialAction = !"CLAIM".equals(action.getAction());
        if (materialAction && !"PENDING".equals(material.getReviewStatus())
                && !"PENDING_REVIEW".equals(material.getReviewStatus()))
        {
            throw new ServiceException("该材料已经处理，不能重复审核");
        }
        if (materialAction
                && !"REVIEW_PENDING".equals(material.getJobStatus())
                && !"PRE_REVIEW_PASSED".equals(material.getJobStatus()))
        {
            throw new ServiceException("Agent尚未完成预审，当前材料不能审核");
        }
        if ("PASS".equals(action.getAction()))
        {
            if (hasPendingAiResults(material.getMaterialId()))
            {
                throw new ServiceException("仍有未确认的AI结果，请逐条确认后再通过");
            }
            applyReviewedValidity(userId, material, reviewer.getUsername());
        }
        if (("RETURN".equals(action.getAction()) || "REJECT".equals(action.getAction()))
                && StringUtils.isEmpty(action.getComment()))
        {
            throw new ServiceException("退回补正必须填写审核意见");
        }
        String previous = profile.getReviewStatus();
        String current;
        String recordedAction = action.getAction();
        switch (action.getAction())
        {
            case "PASS":
                platformMapper.updateMaterialReview(material.getMaterialId(), "APPROVED", action.getComment());
                current = certificationService.profileStatusAfterReview(userId);
                platformMapper.updateProfileReviewStatus(userId, current);
                break;
            case "RETURN":
            case "REJECT":
                platformMapper.updateMaterialReview(material.getMaterialId(), "RETURNED", action.getComment());
                current = certificationService.profileStatusAfterReview(userId);
                platformMapper.updateProfileReviewStatus(userId, current);
                recordedAction = "RETURN";
                break;
            case "CLAIM":
                current = "REVIEWING";
                platformMapper.updateProfileReviewStatus(userId, current);
                break;
            default:
                throw new ServiceException("不支持的审核动作");
        }
        if (materialAction)
        {
            graphMapper.reviewMaterialFacts(material.getMaterialId(),
                    "PASS".equals(action.getAction()) ? "ACCEPTED" : "REJECTED");
            graphMapper.reviewMaterialEvidence(material.getMaterialId(),
                    "PASS".equals(action.getAction()) ? "ACCEPTED" : "REJECTED");
            if ("PASS".equals(action.getAction()))
            {
                graphMapper.applyMaterialSourceReviews(material.getMaterialId());
                graphMapper.applyMaterialTargetReviews(material.getMaterialId());
                graphMapper.applyMaterialRelationTypeReviews(material.getMaterialId());
            }
            platformMapper.updateDimensionAssessmentReview(material.getMaterialId(),
                    "PASS".equals(action.getAction()) ? "ACCEPTED" : "REJECTED");
            if ("PASS".equals(action.getAction()))
            {
                graphMapper.acceptMaterialEntities(material.getMaterialId());
            }
            String eventType = "PASS".equals(action.getAction())
                    ? "MATERIAL_APPROVED" : "MATERIAL_REVIEWED";
            graphOutboxService.enqueue(eventType, "MATERIAL", material.getMaterialId(),
                    Map.of("userId", userId,
                            "materialId", material.getMaterialId(),
                            "action", action.getAction()));
            // 先更新图谱事实状态，再派生画像，避免发布尚未审核的PENDING声明。
            graphProjectionService.projectExpert(userId);
            ExpertProfile refreshed = platformMapper.selectProfileByUserId(userId);
            if (refreshed != null)
            {
                dimensionAggregationService.rebuildDraft(refreshed);
            }
        }
        if (!"APPROVED".equals(current))
        {
            certificationService.suspendServiceIfUncertified(userId, reviewer.getUserId(),
                    reviewer.getUsername());
        }
        else if (materialAction && "PASS".equals(action.getAction()))
        {
            certificationService.enableServiceAfterCertification(userId, reviewer.getUserId(),
                    reviewer.getUsername());
            graphService.publishDraft(userId);
            ExpertProfile approved = platformMapper.selectProfileByUserId(userId);
            platformMapper.upsertApprovedExpert(approved, material.getMaterialId(), reviewer.getUsername(),
                    JSON.toJSONString(approved));
            versionService.captureApprovedState(userId, reviewer.getUsername(),
                    "MATERIAL_APPROVED");
        }
        platformMapper.insertReviewLog(userId, material.getMaterialId(), recordedAction, reviewer.getUserId(),
                reviewer.getUsername(), previous, current, action.getComment());
    }

    @Transactional
    public void reviewAiResult(Long materialId, String resultType, Long resultId,
            String reviewStatus, LoginUser reviewer)
    {
        String status = reviewStatus == null ? "" : reviewStatus.toUpperCase();
        if (!List.of("ACCEPTED", "REJECTED", "PENDING").contains(status))
        {
            throw new ServiceException("不支持的AI结果确认状态");
        }
        ExpertMaterial overview = platformMapper.selectMaterialById(materialId);
        if (overview == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        ExpertProfile profile =
                platformMapper.selectProfileByUserIdForUpdate(overview.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertMaterial material = platformMapper.selectMaterialByIdForUpdate(materialId);
        if (material == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        if (!"PENDING".equals(material.getReviewStatus())
                && !"PENDING_REVIEW".equals(material.getReviewStatus()))
        {
            throw new ServiceException("材料已经完成复审，不能继续修改AI结果");
        }
        if (!"REVIEW_PENDING".equals(material.getJobStatus())
                && !"PRE_REVIEW_PASSED".equals(material.getJobStatus()))
        {
            throw new ServiceException("Agent初审尚未完成，当前不能确认AI结果");
        }
        int updated;
        switch (resultType == null ? "" : resultType.toUpperCase())
        {
            case "FIELD":
                updated = platformMapper.updateFieldMappingReview(materialId, resultId, status, "",
                        reviewer.getUsername());
                break;
            case "DIMENSION":
                updated = platformMapper.updateDimensionAssessmentReviewById(
                        materialId, resultId, status);
                break;
            case "RELATION":
                updated = graphMapper.updateMaterialRelationReview(materialId, resultId, status);
                if (updated > 0)
                {
                    if ("ACCEPTED".equals(status))
                    {
                        graphMapper.applyRelationSourceReview(materialId, resultId);
                        graphMapper.applyRelationTargetReview(materialId, resultId);
                        graphMapper.applyRelationTypeReview(materialId, resultId);
                    }
                    graphMapper.updateRelationReviewStatus(resultId, status);
                }
                break;
            default:
                throw new ServiceException("不支持的AI结果类型");
        }
        if (updated == 0)
        {
            throw new ServiceException("AI结果不存在或已被更新，请刷新后重试");
        }
        platformMapper.insertReviewLog(overview.getUserId(), materialId,
                "ACCEPTED".equals(status) ? "AI_RESULT_ACCEPTED" : "AI_RESULT_REJECTED",
                reviewer.getUserId(), reviewer.getUsername(), "PENDING", status, "");
    }

    @Transactional
    public void editAiResult(Long materialId, String resultType, Long resultId,
            ExpertAiResultEditRequest request, LoginUser reviewer)
    {
        ExpertMaterial overview = platformMapper.selectMaterialById(materialId);
        if (overview == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        ExpertProfile profile =
                platformMapper.selectProfileByUserIdForUpdate(overview.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertMaterial material = platformMapper.selectMaterialByIdForUpdate(materialId);
        if (material == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        if (!"PENDING".equals(material.getReviewStatus())
                && !"PENDING_REVIEW".equals(material.getReviewStatus()))
        {
            throw new ServiceException("材料已经完成复审，不能继续编辑AI结果");
        }
        if (!"REVIEW_PENDING".equals(material.getJobStatus())
                && !"PRE_REVIEW_PASSED".equals(material.getJobStatus()))
        {
            throw new ServiceException("Agent初审尚未完成，当前不能编辑AI结果");
        }
        int updated;
        String type = resultType == null ? "" : resultType.toUpperCase();
        switch (type)
        {
            case "FIELD":
                if (StringUtils.isEmpty(request.getReviewedValue())
                        || StringUtils.isEmpty(request.getReviewedEvidence())
                        || request.getReviewedConfidence() == null)
                {
                    throw new ServiceException("审核提取值、置信度和证据说明不能为空");
                }
                updated = platformMapper.updateFieldMappingOpinion(materialId, resultId,
                        request.getReviewedValue(), request.getReviewedConfidence(),
                        request.getReviewedEvidence(),
                        request.getReviewComment(), reviewer.getUsername());
                break;
            case "DIMENSION":
                if (request.getReviewedScore() == null
                        || StringUtils.isEmpty(request.getReviewedEvidence()))
                {
                    throw new ServiceException("审核评分和证据说明不能为空");
                }
                updated = platformMapper.updateDimensionAssessmentOpinion(materialId,
                        resultId, request.getReviewedScore(), request.getReviewedEvidence(),
                        request.getReviewComment(), reviewer.getUsername());
                break;
            case "RELATION":
                if (StringUtils.isEmpty(request.getReviewedSourceName())
                        || StringUtils.isEmpty(request.getReviewedSourceType())
                        || StringUtils.isEmpty(request.getReviewedRelationType())
                        || StringUtils.isEmpty(request.getReviewedTargetName())
                        || StringUtils.isEmpty(request.getReviewedTargetType())
                        || StringUtils.isEmpty(request.getReviewedExcerpt()))
                {
                    throw new ServiceException("源节点名称/类型、关系类型、目标节点名称/类型和关系证据不能为空");
                }
                if (!ExpertAgentAnalysisValidator.isKnownEntityType(
                        request.getReviewedSourceType())
                        || !ExpertAgentAnalysisValidator.isKnownEntityType(
                                request.getReviewedTargetType()))
                {
                    throw new ServiceException("不支持的节点类型");
                }
                if (!ExpertAgentAnalysisValidator.isKnownRelationType(
                        request.getReviewedRelationType()))
                {
                    throw new ServiceException("不支持的关系类型");
                }
                updated = graphMapper.updateMaterialRelationOpinion(materialId, resultId,
                        request.getReviewedSourceName(), request.getReviewedSourceType(),
                        request.getReviewedRelationType(), request.getReviewedTargetName(),
                        request.getReviewedTargetType(),
                        request.getReviewedExcerpt(), request.getReviewComment(),
                        reviewer.getUsername());
                break;
            default:
                throw new ServiceException("不支持的AI结果类型");
        }
        if (updated == 0)
        {
            throw new ServiceException("AI结果不存在、已处理或已被更新，请刷新后重试");
        }
        platformMapper.insertReviewLog(overview.getUserId(), materialId,
                "AI_RESULT_EDITED", reviewer.getUserId(), reviewer.getUsername(),
                "PENDING", "PENDING", request.getReviewComment());
    }

    private void applyReviewedValidity(Long userId, ExpertMaterial material,
            String reviewerName)
    {
        Map<String, Object> angle = platformMapper.selectCertificationStatus(userId).stream()
                .filter(item -> material.getMaterialCategory().equals(item.get("categoryCode")))
                .findFirst()
                .orElse(null);
        if (angle == null || !"1".equals(String.valueOf(angle.get("validityRequired"))))
        {
            return;
        }
        LocalDate reviewedDate = LocalDate.now();
        LocalDate validUntil = reviewedValidityEnd(reviewedDate);
        platformMapper.updateMaterialValidity(material.getMaterialId(),
                reviewedDate.toString(), validUntil.toString(), reviewerName);
        platformMapper.resolveQualificationRemindersByMaterial(material.getMaterialId());
    }

    static LocalDate reviewedValidityEnd(LocalDate reviewedDate)
    {
        return reviewedDate.plusMonths(3);
    }

    private boolean hasPendingAiResults(Long materialId)
    {
        boolean pendingMapping = platformMapper.selectFieldMappings(materialId).stream()
                .anyMatch(item -> "PENDING".equals(String.valueOf(item.get("reviewStatus"))));
        boolean pendingDimension =
                platformMapper.selectMaterialDimensionAssessments(materialId).stream()
                        .anyMatch(item -> "PENDING".equals(String.valueOf(item.get("reviewStatus"))));
        boolean pendingFact = graphMapper.selectMaterialReviewFacts(materialId).stream()
                .anyMatch(item -> "PENDING".equals(String.valueOf(item.get("reviewStatus"))));
        return pendingMapping || pendingDimension || pendingFact;
    }

    public List<Map<String, Object>> approved(Map<String, Object> query)
    {
        return platformMapper.selectApprovedExperts(query);
    }

    public List<Map<String, Object>> statusRequests(Map<String, Object> query)
    {
        return platformMapper.selectStatusRequests(query);
    }

    @Transactional
    public ExpertStatusRequest reviewStatusRequest(Long requestId, ExpertStatusReviewAction action,
            LoginUser reviewer)
    {
        if (!"APPROVE".equals(action.getAction()) && !"REJECT".equals(action.getAction()))
        {
            throw new ServiceException("不支持的专家状态审核动作");
        }
        if ("REJECT".equals(action.getAction()) && StringUtils.isEmpty(action.getComment()))
        {
            throw new ServiceException("驳回专家状态申请时必须填写审核意见");
        }
        ExpertStatusRequest overview = platformMapper.selectStatusRequestById(requestId);
        if (overview == null)
        {
            throw new ServiceException("专家状态申请不存在");
        }
        // 所有状态审核、专家撤销和管理员状态维护统一先锁专家档案，避免不同入口相互覆盖。
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(overview.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertStatusRequest request = platformMapper.selectStatusRequestByIdForUpdate(requestId);
        if (request == null || !"PENDING".equals(request.getRequestStatus()))
        {
            throw new ServiceException("该专家状态申请已经处理，请刷新后查看最新状态");
        }
        boolean exitRequest = "EXITED".equals(request.getTargetStatus());
        boolean creditRestoreRequest = "CREDIT_BLOCKED".equals(request.getSourceStatus())
                && "AVAILABLE".equals(request.getTargetStatus());
        if (!exitRequest && !creditRestoreRequest)
        {
            throw new ServiceException("不支持该专家状态申请");
        }
        int expectedVersion = action.getVersion() == null
                ? request.getVersion() : action.getVersion();
        if (!Integer.valueOf(expectedVersion).equals(request.getVersion()))
        {
            throw new ServiceException("专家状态申请已被其他审核员处理，请刷新后重试");
        }
        if (!request.getSourceStatus().equals(profile.getServiceStatus()))
        {
            throw new ServiceException("专家当前接单状态已发生变化，请驳回该申请并要求重新提交");
        }
        if ("APPROVE".equals(action.getAction()))
        {
            if (platformMapper.updateProfileServiceStatusIfCurrent(request.getUserId(),
                    request.getSourceStatus(), request.getTargetStatus()) == 0)
            {
                throw new ServiceException("专家接单状态已发生变化，请驳回该申请并要求重新提交");
            }
            String reason = request.getReason();
            if (StringUtils.isNotEmpty(action.getComment()))
            {
                reason = reason + "；审核意见：" + action.getComment().trim();
            }
            platformMapper.insertStatusLog(request.getUserId(), request.getSourceStatus(),
                    request.getTargetStatus(), reason, request.getCreditRelated(),
                    reviewer.getUserId(), reviewer.getUsername());
            ExpertProfile refreshed = platformMapper.selectProfileByUserId(request.getUserId());
            if (refreshed != null)
            {
                platformMapper.syncApprovedProfile(refreshed);
            }
            graphProjectionService.projectExpert(request.getUserId());
            if (creditRestoreRequest)
            {
                creditService.restoreAfterApprovedRequest(request.getUserId(),
                        request.getRequestId(), reviewer);
            }
            graphOutboxService.enqueue("EXPERT_STATUS_CHANGED", "EXPERT", request.getUserId(),
                    Map.of("userId", request.getUserId(),
                            "serviceStatus", request.getTargetStatus()));
        }
        if (platformMapper.resolveStatusRequest(requestId, expectedVersion,
                "APPROVE".equals(action.getAction()) ? "APPROVED" : "REJECTED",
                reviewer.getUserId(), reviewer.getUsername(), action.getComment()) == 0)
        {
            throw new ServiceException("该专家状态申请已被其他审核员处理，请刷新后重试");
        }
        return platformMapper.selectStatusRequestById(requestId);
    }

    @Transactional
    public void retryAgent(Long materialId)
    {
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null)
        {
            throw new ServiceException("资料文件不存在");
        }
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(material.getUserId());
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

    public Map<String, Object> summary()
    {
        Map<String, Object> result = new HashMap<>();
        result.put("pendingCount", platformMapper.countQueue());
        result.put("agentFailureCount", platformMapper.countAgentFailures());
        result.put("approvedCount", platformMapper.countApprovedExperts());
        result.put("pausedCount", platformMapper.countPausedExperts());
        result.put("pendingStatusRequestCount", platformMapper.countPendingStatusRequests());
        return result;
    }

    public List<Map<String, Object>> jobs(Map<String, Object> query)
    {
        return platformMapper.selectProcessingJobs(query);
    }

    public Map<String, Object> adminSummary()
    {
        Map<String, Object> result = summary();
        LoginUser user = SecurityUtils.getLoginUser();
        result.put("currentUser", user.getUsername());
        return result;
    }
}
