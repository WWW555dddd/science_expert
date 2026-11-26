package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertBusinessServiceTest
{
    @Mock
    private ExpertBusinessMapper businessMapper;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertOpenApiService openApiService;

    @InjectMocks
    private ExpertBusinessService businessService;

    @Test
    void returnsPublishedProfileSchedulesAndConflictInformation()
    {
        ExpertProfile profile = approvedProfile(12L);
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(1);
        schedule.setStartTime("07:30");
        schedule.setEndTime("09:30");
        Map<String, Object> release = Map.of(
                "releaseId", 9L,
                "versionNo", 3,
                "graphVersion", 5,
                "approvedBy", "reviewer",
                "triggerType", "MATERIAL_APPROVED",
                "createTime", "2026-11-19 09:00:00",
                "profileSnapshotJson", publishedSnapshot());
        Map<String, Object> declaration = Map.of(
                "materialId", 77L,
                "fileName", "利益冲突说明.pdf",
                "versionNo", 2,
                "reviewStatus", "APPROVED");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L)).thenReturn(List.of(schedule));
        when(platformMapper.selectLatestExpertReleaseVersion(12L)).thenReturn(release);
        when(businessMapper.selectLatestConflictDisclosure(12L)).thenReturn(declaration);

        Map<String, Object> result = businessService.expertDetail(12L);

        assertEquals(true, result.get("readOnly"));
        assertEquals(30, ((List<?>) result.get("dimensions")).size());
        assertEquals(List.of(schedule), result.get("schedules"));
        assertTrue(result.get("notice").toString().contains("不提供"));
        Map<?, ?> conflict = (Map<?, ?>) result.get("conflict");
        assertEquals(4, ((List<?>) conflict.get("dimensions")).size());
    }

    @Test
    void rejectsExitedExpertForBusinessUse()
    {
        ExpertProfile profile = approvedProfile(12L);
        profile.setServiceStatus("EXITED");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.ruoyi.common.exception.ServiceException.class,
                () -> businessService.expertDetail(12L));
    }

    @Test
    void delegatesTaskEligibilityToReadOnlyOpenApiService()
    {
        ExpertProfile profile = approvedProfile(12L);
        ExpertEligibilityRequest request = new ExpertEligibilityRequest();
        request.setUserId(12L);
        Map<String, Object> expected = Map.of("decision", "ELIGIBLE");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(openApiService.checkEligibility(request)).thenReturn(expected);

        Map<String, Object> result = businessService.checkTaskEligibility(request);

        assertEquals(expected, result);
        verify(openApiService).checkEligibility(request);
    }

    @Test
    void rejectsAcceptanceRequestWhenLatestEligibilityIsIneligible()
    {
        ExpertProfile profile = approvedProfile(12L);
        ExpertEligibilityRequest request = new ExpertEligibilityRequest();
        request.setUserId(12L);
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(openApiService.checkEligibility(request)).thenReturn(
                Map.of("decision", ExpertOpenApiService.INELIGIBLE));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.ruoyi.common.exception.ServiceException.class,
                () -> businessService.ensureAcceptanceRequestEligible(request));
        verify(openApiService).checkEligibility(request);
    }

    @Test
    void excludesOccupancyRowsWithoutExternalTaskId()
    {
        ExpertProfile profile = approvedProfile(12L);
        Map<String, Object> withoutTask = new HashMap<>();
        withoutTask.put("externalTaskId", null);
        Map<String, Object> withTask = Map.of("externalTaskId", "TASK-001");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(businessMapper.selectOccupancySnapshots(
                12L, "2026-11-24 09:00:00", "2026-11-24 12:00:00"))
                .thenReturn(List.of(withoutTask, withTask));

        List<Map<String, Object>> result = businessService.occupancySnapshots(
                12L, "2026-11-24 09:00:00", "2026-11-24 12:00:00");

        assertEquals(List.of(withTask), result);
    }

    @Test
    void showsOnlyLatestStateForTheSameTaskAndTime()
    {
        ExpertProfile profile = approvedProfile(12L);
        Map<String, Object> freeSnapshot = Map.of(
                "contextId", 1L,
                "externalTaskId", "TASK-001",
                "serviceStart", "2026-11-24 09:00:00",
                "serviceEnd", "2026-11-24 12:00:00",
                "occupancyStatus", "FREE");
        Map<String, Object> occupiedSnapshot = Map.of(
                "contextId", 2L,
                "externalTaskId", "TASK-001",
                "serviceStart", "2026-11-24 09:00:00",
                "serviceEnd", "2026-11-24 12:00:00",
                "occupancyStatus", "OCCUPIED");
        Map<String, Object> anotherTask = Map.of(
                "contextId", 3L,
                "externalTaskId", "TASK-002",
                "serviceStart", "2026-11-25 09:00:00",
                "serviceEnd", "2026-11-25 12:00:00",
                "occupancyStatus", "FREE");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(businessMapper.selectOccupancySnapshots(
                12L, "2026-11-24 00:00:00", "2026-11-26 00:00:00"))
                .thenReturn(List.of(freeSnapshot, occupiedSnapshot, anotherTask));

        List<Map<String, Object>> result = businessService.occupancySnapshots(
                12L, "2026-11-24 00:00:00", "2026-11-26 00:00:00");

        assertEquals(2, result.size());
        assertEquals("OCCUPIED", result.get(0).get("occupancyStatus"));
        assertEquals("TASK-002", result.get(1).get("externalTaskId"));
    }

    private static ExpertProfile approvedProfile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setRealName("测试专家");
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        profile.setDelFlag("0");
        profile.setGraphVersion(5);
        return profile;
    }

    private static String publishedSnapshot()
    {
        StringBuilder dimensions = new StringBuilder("[");
        for (int index = 0; index < 30; index++)
        {
            if (index > 0)
            {
                dimensions.append(',');
            }
            String group = index >= 26 ? "CONFLICT" : "QUALIFICATION";
            String code = index >= 26 ? "CONFLICT_" + index : "QUAL_" + index;
            dimensions.append("{\"dimensionCode\":\"").append(code)
                    .append("\",\"groupCode\":\"").append(group)
                    .append("\",\"dimensionName\":\"维度").append(index)
                    .append("\",\"score\":80,\"dataStatus\":\"AVAILABLE\"}");
        }
        dimensions.append(']');
        return "{\"profile\":{\"realName\":\"测试专家\"},\"dimensions\":"
                + dimensions + "}";
    }
}
