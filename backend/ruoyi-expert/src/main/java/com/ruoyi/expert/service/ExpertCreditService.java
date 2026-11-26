package com.ruoyi.expert.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertRiskReview;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 专家信誉积分、风险扣分和信誉恢复。
 */
@Service
public class ExpertCreditService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Value("${expert.credit.block-threshold:60}")
    private int blockThreshold;

    @Value("${expert.credit.restore-score:60}")
    private int restoreScore;

    @Value("${expert.credit.low-risk-deduction:2}")
    private int lowRiskDeduction;

    @Value("${expert.credit.medium-risk-deduction:5}")
    private int mediumRiskDeduction;

    @Value("${expert.credit.high-risk-deduction:10}")
    private int highRiskDeduction;

    @Transactional
    public Map<String, Object> deductForRisk(ExpertRiskReview risk, LoginUser reviewer)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(risk.getUserId());
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        int before = profile.getCreditScore() == null ? 100 : profile.getCreditScore();
        int deduction = deduction(risk.getSeverity());
        int after = Math.max(0, before - deduction);
        platformMapper.updateCreditScore(risk.getUserId(), after);
        platformMapper.insertCreditLog(risk.getUserId(), "RISK_DEDUCTION",
                risk.getRiskId(), null, before, after, after - before,
                "风险预警审核确认：" + risk.getTitle(),
                reviewer.getUserId(), reviewer.getUsername());

        boolean blocked = false;
        String previousStatus = profile.getServiceStatus();
        if (after < blockThreshold
                && "APPROVED".equals(profile.getReviewStatus())
                && !"CREDIT_BLOCKED".equals(previousStatus)
                && !"EXITED".equals(previousStatus))
        {
            if (platformMapper.updateProfileServiceStatusIfCurrent(risk.getUserId(),
                    previousStatus, "CREDIT_BLOCKED") > 0)
            {
                platformMapper.insertStatusLog(risk.getUserId(), previousStatus,
                        "CREDIT_BLOCKED",
                        "风险预警确认后信誉分降至 " + after + "，低于阈值 "
                                + blockThreshold + "，系统自动禁止接单",
                        "1", reviewer.getUserId(), reviewer.getUsername());
                platformMapper.cancelPendingStatusRequests(risk.getUserId(),
                        reviewer.getUsername(),
                        "信誉分低于阈值自动禁止接单，待审状态申请自动撤销");
                profile.setServiceStatus("CREDIT_BLOCKED");
                platformMapper.syncApprovedProfile(profile);
                blocked = true;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beforeScore", before);
        result.put("afterScore", after);
        result.put("deduction", deduction);
        result.put("blockThreshold", blockThreshold);
        result.put("blocked", blocked || "CREDIT_BLOCKED".equals(previousStatus));
        result.put("serviceStatus", blocked ? "CREDIT_BLOCKED" : previousStatus);
        return result;
    }

    @Transactional
    public Map<String, Object> restoreAfterApprovedRequest(Long userId, Long requestId,
            LoginUser reviewer)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        int before = profile.getCreditScore() == null ? 0 : profile.getCreditScore();
        int after = Math.max(before, restoreScore);
        platformMapper.updateCreditScore(userId, after);
        platformMapper.insertCreditLog(userId, "RESTORE_APPROVED", null, requestId,
                before, after, after - before, "信誉恢复申请审核通过",
                reviewer.getUserId(), reviewer.getUsername());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("beforeScore", before);
        result.put("afterScore", after);
        result.put("restoreScore", restoreScore);
        return result;
    }

    public boolean isBelowThreshold(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        int score = profile == null || profile.getCreditScore() == null
                ? 100 : profile.getCreditScore();
        return score < blockThreshold;
    }

    public int getBlockThreshold()
    {
        return blockThreshold;
    }

    private int deduction(String severity)
    {
        return switch (severity == null ? "" : severity.toUpperCase())
        {
            case "HIGH" -> highRiskDeduction;
            case "LOW" -> lowRiskDeduction;
            default -> mediumRiskDeduction;
        };
    }
}
