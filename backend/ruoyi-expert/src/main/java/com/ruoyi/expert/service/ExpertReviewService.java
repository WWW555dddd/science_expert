package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertReviewAction;
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
        List<ExpertMaterial> materials = platformMapper.selectMaterialList(new ExpertMaterial()
        {{
            setUserId(userId);
        }});
        result.put("materials", materials);
        result.put("mappings", materialId == null ? List.of() : platformMapper.selectFieldMappings(materialId));
        Map<Long, List<Map<String, Object>>> mappingsByMaterial = new HashMap<>();
        for (ExpertMaterial material : materials)
        {
            mappingsByMaterial.put(material.getMaterialId(),
                    platformMapper.selectFieldMappings(material.getMaterialId()));
        }
        result.put("mappingsByMaterial", mappingsByMaterial);
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
        else if (materialAction)
        {
            certificationService.enableServiceAfterCertification(userId, reviewer.getUserId(),
                    reviewer.getUsername());
            graphService.publishDraft(userId);
            ExpertProfile approved = platformMapper.selectProfileByUserId(userId);
            platformMapper.upsertApprovedExpert(approved, material.getMaterialId(), reviewer.getUsername(),
                    JSON.toJSONString(approved));
        }
        platformMapper.insertReviewLog(userId, material.getMaterialId(), recordedAction, reviewer.getUserId(),
                reviewer.getUsername(), previous, current, action.getComment());
    }

    public List<Map<String, Object>> approved(Map<String, Object> query)
    {
        return platformMapper.selectApprovedExperts(query);
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
