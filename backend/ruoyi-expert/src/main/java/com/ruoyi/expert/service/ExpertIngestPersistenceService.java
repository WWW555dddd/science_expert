package com.ruoyi.expert.service;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * Agent 结果的短事务落库。远程 OCR 和 AI 调用必须在事务外执行。
 */
@Service
public class ExpertIngestPersistenceService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertGraphService graphService;

    @Autowired
    private KnowledgeGraphOutboxService graphOutboxService;

    @Autowired
    private KnowledgeGraphFactPersistenceService graphFactPersistenceService;

    @Autowired
    private ExpertProfileDimensionAggregationService dimensionAggregationService;

    @Autowired
    private ExpertVersionService versionService;

    @Transactional
    public boolean persist(PersistCommand command)
    {
        // 所有异步结果最终都通过专家档案行锁串行落库。
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(command.userId());
        if (profile == null)
        {
            throw new ServiceException("专家档案已被删除，Agent结果不能落库");
        }
        int updated = platformMapper.updateMaterialJob(command.materialId(), command.processingToken(),
                command.jobStatus(), command.reviewStatus(), command.mappingCount(),
                command.issueCount(), command.message());
        if (updated == 0)
        {
            // 任务令牌已经变化，说明当前任务已过期或被更新的任务替代。
            return false;
        }

        platformMapper.deleteFieldMappings(command.materialId());
        for (FieldMappingDraft mapping : command.mappings())
        {
            platformMapper.insertFieldMapping(command.materialId(), mapping.dbField(),
                    mapping.fileField(), mapping.value(), mapping.confidence(),
                    mapping.evidence(), mapping.conflictFlag());
        }

        ExpertMaterial material = platformMapper.selectMaterialById(command.materialId());
        ExpertMaterial latest = material == null ? null
                : platformMapper.selectLatestMaterialByUserIdAndCategory(
                        command.userId(), material.getMaterialCategory());
        boolean latestVersion = latest != null
                && command.materialId().equals(latest.getMaterialId());
        if (!latestVersion)
        {
            // 旧版本保留自己的提取结果，但不能覆盖当前版本的全局画像和认证状态。
            enqueueProcessedEvent(command);
            return true;
        }

        graphFactPersistenceService.persist(command.userId(), command.materialId(),
                command.graphFacts(), command.graphFactSource(), command.adminApproved());
        dimensionAggregationService.recordAndAggregate(profile, command.materialId(),
                command.remoteAnalysis(), command.adminApproved());

        if (!command.adminApproved())
        {
            platformMapper.markProfilePendingIfNotApproved(command.userId());
        }
        else if (platformMapper.countAgentReadyRequiredAngles(command.userId())
                >= platformMapper.countRequiredAngles())
        {
            graphService.publishDraft(command.userId());
            ExpertProfile approvedProfile = platformMapper.selectProfileByUserId(command.userId());
            platformMapper.upsertApprovedExpert(approvedProfile, command.materialId(),
                    "管理员模型辅助直审", JSON.toJSONString(approvedProfile));
        }
        if (command.adminApproved())
        {
            versionService.captureApprovedState(command.userId(), "管理员模型辅助直审",
                    "ADMIN_MATERIAL_APPROVED");
        }
        enqueueProcessedEvent(command);
        return true;
    }

    private void enqueueProcessedEvent(PersistCommand command)
    {
        graphOutboxService.enqueue(
                command.adminApproved() ? "MATERIAL_APPROVED" : "MATERIAL_PROCESSED",
                "MATERIAL",
                command.materialId(),
                Map.of("materialId", command.materialId(),
                        "userId", command.userId(),
                        "mappingCount", command.mappingCount(),
                        "issueCount", command.issueCount()));
    }

    public record FieldMappingDraft(String dbField, String fileField, String value,
            double confidence, String evidence, String conflictFlag)
    {
    }

    public record PersistCommand(Long materialId, String processingToken, Long userId,
            String jobStatus, String reviewStatus, int mappingCount, int issueCount,
            String message, boolean adminApproved, List<FieldMappingDraft> mappings,
            JSONObject remoteAnalysis, JSONObject graphFacts, String graphFactSource)
    {
    }
}
