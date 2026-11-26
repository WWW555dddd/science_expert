package com.ruoyi.expert.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 资质到期提醒与过期接单门禁。
 */
@Service
public class ExpertQualificationReminderService
{
    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertCertificationService certificationService;

    @Value("${expert.qualification.reminder-days:30}")
    private int reminderDays;

    @Transactional
    @Scheduled(fixedDelayString = "${expert.qualification.reminder-delay-ms:3600000}",
            initialDelayString = "${expert.qualification.reminder-initial-delay-ms:60000}")
    public void refreshReminders()
    {
        List<Map<String, Object>> candidates =
                platformMapper.selectQualificationReminderCandidates(reminderDays);
        for (Map<String, Object> candidate : candidates)
        {
            platformMapper.resolveQualificationReminderTypeChange(
                    ((Number) candidate.get("materialId")).longValue(),
                    String.valueOf(candidate.get("reminderType")));
            platformMapper.insertQualificationReminder(candidate);
        }
        platformMapper.resolveStaleQualificationReminders(reminderDays);

        Set<Long> expiredUsers = new LinkedHashSet<>();
        for (Map<String, Object> candidate : candidates)
        {
            if ("EXPIRED".equals(candidate.get("reminderType")))
            {
                expiredUsers.add(((Number) candidate.get("userId")).longValue());
            }
        }
        for (Long userId : expiredUsers)
        {
            gateExpiredQualification(userId);
        }
    }

    public List<Map<String, Object>> reminders(Long userId)
    {
        return platformMapper.selectQualificationReminders(userId);
    }

    private void gateExpiredQualification(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserIdForUpdate(userId);
        if (profile == null)
        {
            return;
        }
        String status = certificationService.profileStatusAfterReview(userId);
        if (!status.equals(profile.getReviewStatus()))
        {
            platformMapper.updateProfileReviewStatus(userId, status);
        }
        certificationService.suspendServiceIfUncertified(userId, userId, "系统");
    }
}
