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
        // 正式画像只能由当前有效、已审核的图谱声明生成。
        dimensionAggregationService.rebuildDraft(profile, true);
        int expectedDimensions = platformMapper.countEnabledGraphDimensions();
        int draftDimensions = platformMapper.countDraftGraph(userId);
        if (expectedDimensions == 0 || draftDimensions < expectedDimensions)
        {
            // 缺失维度代表证据不足，不允许把残缺画像发布为正式版本。
            return false;
        }
        platformMapper.incrementGraphVersion(userId);
        platformMapper.deleteApprovedGraph(userId);
        int approved = platformMapper.approveDraftGraph(userId);
        if (approved > 0)
        {
            platformMapper.approveDraftGraphEvidence(userId);
        }
        return approved > 0;
    }
}
