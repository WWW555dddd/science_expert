package com.ruoyi.expert.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 专家认证角度与整体认证状态计算。
 */
@Service
public class ExpertCertificationService
{
    private static final int EXPIRING_DAYS = 30;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertCreditService creditService;

    public Map<String, Object> status(Long userId)
    {
        List<Map<String, Object>> angles = platformMapper.selectCertificationStatus(userId);
        int requiredTotal = platformMapper.countRequiredAngles();
        int passedRequired = platformMapper.countApprovedRequiredAngles(userId);
        int needsUpdate = 0;
        int inReview = 0;
        int expiring = 0;
        int uploaded = 0;

        for (Map<String, Object> angle : angles)
        {
            String status = angleStatus(angle);
            angle.put("certificationStatus", status);
            angle.put("certificationStatusText", statusText(status));
            boolean required = "1".equals(String.valueOf(angle.get("requiredFlag")));
            if (angle.get("materialId") != null)
            {
                uploaded++;
            }
            if (required && ("NEEDS_UPDATE".equals(status) || "EXPIRED".equals(status)))
            {
                needsUpdate++;
            }
            if (required && "IN_REVIEW".equals(status))
            {
                inReview++;
            }
            if (required && "EXPIRING_SOON".equals(status))
            {
                expiring++;
            }
        }

        boolean fullyCertified = requiredTotal > 0 && passedRequired >= requiredTotal;
        String overallStatus;
        if (fullyCertified)
        {
            overallStatus = "CERTIFIED";
        }
        else if (needsUpdate > 0)
        {
            overallStatus = "NEEDS_UPDATE";
        }
        else if (uploaded > 0)
        {
            overallStatus = "IN_PROGRESS";
        }
        else
        {
            overallStatus = "NOT_STARTED";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("angles", angles);
        result.put("requiredTotal", requiredTotal);
        result.put("passedRequired", passedRequired);
        result.put("needsUpdateCount", needsUpdate);
        result.put("inReviewCount", inReview);
        result.put("expiringCount", expiring);
        result.put("fullyCertified", fullyCertified);
        result.put("overallStatus", overallStatus);
        result.put("overallStatusText", overallText(overallStatus));
        return result;
    }

    public String profileStatusAfterReview(Long userId)
    {
        Map<String, Object> certification = status(userId);
        if (Boolean.TRUE.equals(certification.get("fullyCertified")))
        {
            return "APPROVED";
        }
        if ("NEEDS_UPDATE".equals(certification.get("overallStatus")))
        {
            return "RETURNED";
        }
        if ("NOT_STARTED".equals(certification.get("overallStatus")))
        {
            return "DRAFT";
        }
        return "PENDING_REVIEW";
    }

    public boolean isCertified(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null || !"APPROVED".equals(profile.getReviewStatus()))
        {
            return false;
        }
        return Boolean.TRUE.equals(status(userId).get("fullyCertified"));
    }

    public void suspendServiceIfUncertified(Long userId, Long operatorId, String operatorName)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null || !"AVAILABLE".equals(profile.getServiceStatus()) || isCertified(userId))
        {
            return;
        }
        platformMapper.updateProfileServiceStatus(userId, "PENDING_CERTIFICATION");
        platformMapper.insertStatusLog(userId, "AVAILABLE", "PENDING_CERTIFICATION",
                "专家尚未正式认证或认证材料正在重新审核，系统自动暂停接单",
                "0", operatorId, operatorName);
    }

    public void enableServiceAfterCertification(Long userId, Long operatorId, String operatorName)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null || !"PENDING_CERTIFICATION".equals(profile.getServiceStatus())
                || !isCertified(userId))
        {
            return;
        }
        boolean creditBlocked = creditService.isBelowThreshold(userId);
        String targetStatus = creditBlocked ? "CREDIT_BLOCKED" : "AVAILABLE";
        platformMapper.updateProfileServiceStatus(userId, targetStatus);
        platformMapper.insertStatusLog(userId, "PENDING_CERTIFICATION", targetStatus,
                creditBlocked
                        ? "专家正式认证通过，但信誉分低于阈值，系统禁止接单"
                        : "专家正式认证通过，系统自动开放接单",
                creditBlocked ? "1" : "0", operatorId, operatorName);
    }

    private static String angleStatus(Map<String, Object> angle)
    {
        Object materialId = angle.get("materialId");
        Object effectiveMaterialId =
                angle.containsKey("effectiveMaterialId") ? angle.get("effectiveMaterialId") : null;
        if (materialId == null)
        {
            return "NOT_UPLOADED";
        }
        String reviewStatus = String.valueOf(angle.get("reviewStatus"));
        String validityRequired = String.valueOf(angle.get("validityRequired"));
        Object validUntilValue = angle.get("validUntil");
        LocalDate validUntil = parseDate(validUntilValue);
        if ("APPROVED".equals(reviewStatus))
        {
            if ("1".equals(validityRequired) && validUntil != null)
            {
                LocalDate today = LocalDate.now();
                if (validUntil.isBefore(today))
                {
                    return "EXPIRED";
                }
                if (!validUntil.isAfter(today.plusDays(EXPIRING_DAYS)))
                {
                    return "EXPIRING_SOON";
                }
            }
            return "APPROVED";
        }
        if ("RETURNED".equals(reviewStatus) || "REJECTED".equals(reviewStatus))
        {
            if (effectiveMaterialId != null)
            {
                return "UPDATE_RETURNED";
            }
            return "NEEDS_UPDATE";
        }
        if (effectiveMaterialId != null)
        {
            return "IN_REVIEW";
        }
        return "IN_REVIEW";
    }

    private static String statusText(String status)
    {
        return switch (status)
        {
            case "APPROVED" -> "已通过认证";
            case "EXPIRING_SOON" -> "资质即将过期";
            case "EXPIRED" -> "资质已过期，暂停接单资格";
            case "IN_REVIEW" -> "审核中";
            case "UPDATE_RETURNED" -> "最新更新未通过，原版本继续生效";
            case "NEEDS_UPDATE" -> "未通过，请重新上传";
            default -> "尚未上传";
        };
    }

    private static LocalDate parseDate(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String text = String.valueOf(value);
        if (text.length() < 10)
        {
            return null;
        }
        try
        {
            return LocalDate.parse(text.substring(0, 10));
        }
        catch (DateTimeParseException ex)
        {
            return null;
        }
    }

    private static String overallText(String status)
    {
        return switch (status)
        {
            case "CERTIFIED" -> "专家整体认证已通过";
            case "NEEDS_UPDATE" -> "部分认证角度需要补正";
            case "IN_PROGRESS" -> "专家认证进行中";
            default -> "尚未开始认证";
        };
    }
}
