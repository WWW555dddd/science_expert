package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertVersionServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertDimensionViewService dimensionViewService;

    @Mock
    private KnowledgeGraphQueryService graphQueryService;

    @InjectMocks
    private ExpertVersionService versionService;

    @Test
    void capturesMaterialProfileAndGraphAsOneImmutableRelease()
    {
        Long userId = 106L;
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("categoryCode", "IDENTITY");
        material.put("materialId", 74L);
        material.put("versionNo", 1);
        material.put("fileName", "identity.pdf");
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setGraphVersion(0);
        Map<String, Object> graphSnapshot = new LinkedHashMap<>();
        graphSnapshot.put("status", "READY");
        graphSnapshot.put("entities", List.of(Map.of("entityId", 1846L)));
        graphSnapshot.put("relations", List.of());
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("releaseId", 8L);
        saved.put("userId", userId);
        saved.put("versionNo", 1);
        saved.put("graphVersion", 0);
        saved.put("sourceMaterialVersionsJson", JSON.toJSONString(List.of(material)));
        saved.put("profileSnapshotJson",
                JSON.toJSONString(Map.of("dimensions", List.of(), "status", "DRAFT")));
        saved.put("graphSnapshotJson", JSON.toJSONString(graphSnapshot));
        saved.put("approvedBy", "reviewer");
        saved.put("triggerType", "MATERIAL_APPROVED");

        when(platformMapper.selectEffectiveMaterialVersions(userId))
                .thenReturn(List.of(material));
        when(platformMapper.selectLatestExpertReleaseVersion(userId))
                .thenReturn(null, saved);
        when(platformMapper.selectProfileByUserId(userId)).thenReturn(profile);
        when(dimensionViewService.dimensions(userId, "DRAFT")).thenReturn(List.of());
        when(graphQueryService.expertSubgraph(userId, 3)).thenReturn(graphSnapshot);
        when(platformMapper.selectExpertReleaseVersion(userId, 8L)).thenReturn(saved);

        Map<String, Object> release = versionService.captureApprovedState(
                userId, "reviewer", "MATERIAL_APPROVED");

        assertNotNull(release);
        assertEquals(1, release.get("versionNo"));
        assertEquals(1, ((List<?>) release.get("sourceMaterialVersions")).size());
        verify(platformMapper).insertExpertReleaseVersion(anyMap());
    }

    @Test
    void capturesNewPartialReleaseWhenOnlyGraphVersionChanges()
    {
        Long userId = 107L;
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("categoryCode", "IDENTITY");
        material.put("materialId", 79L);
        material.put("versionNo", 1);
        material.put("fileName", "identity.pdf");
        String materialSignature = JSON.toJSONString(List.of(material));

        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setGraphVersion(1);
        Map<String, Object> latest = new LinkedHashMap<>();
        latest.put("releaseId", 20L);
        latest.put("versionNo", 2);
        latest.put("graphVersion", 0);
        latest.put("sourceMaterialVersionsJson", materialSignature);
        Map<String, Object> saved = new LinkedHashMap<>(latest);
        saved.put("releaseId", 21L);
        saved.put("versionNo", 3);
        saved.put("graphVersion", 1);
        saved.put("profileSnapshotJson", "{}");
        saved.put("graphSnapshotJson", "{}");
        saved.put("sourceMaterialVersionsJson", materialSignature);
        saved.put("approvedBy", "reviewer");
        saved.put("triggerType", "MATERIAL_APPROVED");
        ExpertGraphDimension available = new ExpertGraphDimension();
        available.setDataStatus("AVAILABLE");
        ExpertGraphDimension missing = new ExpertGraphDimension();
        missing.setDataStatus("INSUFFICIENT_EVIDENCE");

        when(platformMapper.selectEffectiveMaterialVersions(userId))
                .thenReturn(List.of(material));
        when(platformMapper.selectLatestExpertReleaseVersion(userId))
                .thenReturn(latest, saved);
        when(platformMapper.selectProfileByUserId(userId)).thenReturn(profile);
        when(dimensionViewService.dimensions(userId, "DRAFT"))
                .thenReturn(List.of(available, missing));
        when(graphQueryService.expertSubgraph(userId, 3)).thenReturn(Map.of());
        when(platformMapper.selectExpertReleaseVersion(userId, 21L)).thenReturn(saved);

        versionService.captureApprovedState(userId, "reviewer", "MATERIAL_APPROVED");

        ArgumentCaptor<Map<String, Object>> releaseCaptor =
                ArgumentCaptor.forClass(Map.class);
        verify(platformMapper).insertExpertReleaseVersion(releaseCaptor.capture());
        JSONObject snapshot = JSON.parseObject(
                String.valueOf(releaseCaptor.getValue().get("profileSnapshotJson")));
        assertEquals("PARTIAL", snapshot.getString("status"));
        assertEquals(1, snapshot.getIntValue("availableDimensionCount"));
        assertEquals(1, snapshot.getIntValue("insufficientDimensionCount"));
    }
}
