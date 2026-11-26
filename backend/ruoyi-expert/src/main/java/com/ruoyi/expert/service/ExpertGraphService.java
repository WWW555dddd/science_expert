package com.ruoyi.expert.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 专家能力画像草稿转正式版本。
 */
@Service
public class ExpertGraphService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertProfileDimensionAggregationService dimensionAggregationService;

    /**
     * 同一个专家、同一个维度只能保留一条正式画像。
     * 先删除旧正式版本，再把当前草稿整体升级，避免唯一键冲突。
     */
    @Transactional
    public boolean publishDraft(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            return false;
        }
        // 正式画像只能由当前有效、已审核的独立材料维度评估生成。
        dimensionAggregationService.rebuildDraft(profile, true);
        int draftDimensions = platformMapper.countDraftGraph(userId);
        if (draftDimensions == 0)
        {
            // 没有任何有效维度时没有画像内容可发布；部分维度有分数时可以发布部分画像。
            return false;
        }
        platformMapper.incrementGraphVersion(userId);
        platformMapper.deleteApprovedGraph(userId);
        int approved = platformMapper.approveDraftGraph(userId);
        if (approved > 0)
        {
            platformMapper.deleteApprovedDimensionEvidence(userId);
            platformMapper.approveDraftGraphEvidence(userId);
            // 保留一份与最新已审核材料同步的当前草稿，供检索和页面读取；
            // APPROVED 记录仍是不可被后续编辑覆盖的正式历史版本。
            profile.setGraphVersion((profile.getGraphVersion() == null ? 0
                    : profile.getGraphVersion()) + 1);
            dimensionAggregationService.rebuildDraft(profile, true);
        }
        return approved > 0;
    }
}
