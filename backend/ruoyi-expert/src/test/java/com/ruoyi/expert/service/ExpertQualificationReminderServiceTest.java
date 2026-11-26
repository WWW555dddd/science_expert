package com.ruoyi.expert.service;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertQualificationReminderServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertCertificationService certificationService;

    @InjectMocks
    private ExpertQualificationReminderService reminderService;

    @BeforeEach
    void setUp()
    {
        ReflectionTestUtils.setField(reminderService, "reminderDays", 30);
    }

    @Test
    void expiredCredentialCreatesReminderAndRevokesAcceptingOrders()
    {
        Map<String, Object> expired = Map.of(
                "userId", 9L,
                "materialId", 71L,
                "materialCategory", "CERTIFICATE",
                "expiryDate", "2026-11-01",
                "reminderType", "EXPIRED");
        when(platformMapper.selectQualificationReminderCandidates(30))
                .thenReturn(List.of(expired));
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(9L);
        profile.setReviewStatus("APPROVED");
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(certificationService.profileStatusAfterReview(9L)).thenReturn("RETURNED");

        reminderService.refreshReminders();

        verify(platformMapper).resolveQualificationReminderTypeChange(71L, "EXPIRED");
        verify(platformMapper).insertQualificationReminder(anyMap());
        verify(platformMapper).resolveStaleQualificationReminders(30);
        verify(platformMapper).updateProfileReviewStatus(9L, "RETURNED");
        verify(certificationService).suspendServiceIfUncertified(9L, 9L, "系统");
    }
}
