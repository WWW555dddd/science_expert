package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertIngestPersistenceServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertGraphService graphService;

    @Mock
    private KnowledgeGraphOutboxService graphOutboxService;

    @Mock
    private KnowledgeGraphFactPersistenceService graphFactPersistenceService;

    @Mock
    private ExpertProfileDimensionAggregationService dimensionAggregationService;

    @InjectMocks
    private ExpertIngestPersistenceService persistenceService;

    @Test
    void staleProcessingTokenCannotReplaceNewMappingsOrGraph()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(9L);
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(platformMapper.updateMaterialJob(11L, "stale", "REVIEW_PENDING", "PENDING",
                1, 0, "done")).thenReturn(0);
        ExpertIngestPersistenceService.PersistCommand command =
                new ExpertIngestPersistenceService.PersistCommand(
                        11L, "stale", 9L, "REVIEW_PENDING", "PENDING",
                        1, 0, "done", false,
                        List.of(new ExpertIngestPersistenceService.FieldMappingDraft(
                                "real_name", "姓名", "张三", 0.9, "正文", "0")),
                        null, null, null);

        assertFalse(persistenceService.persist(command));

        verify(platformMapper, never()).deleteFieldMappings(anyLong());
        verify(platformMapper, never()).deleteDraftGraph(anyLong());
        verify(platformMapper, never()).markProfilePendingIfNotApproved(anyLong());
    }

    @Test
    void resultPersistenceLocksProfileBeforeWrites()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setGraphVersion(1);
        when(platformMapper.selectProfileByUserIdForUpdate(12L)).thenReturn(profile);
        when(platformMapper.updateMaterialJob(anyLong(), anyString(), anyString(), anyString(),
                anyInt(), anyInt(), anyString())).thenReturn(1);
        ExpertMaterial material = new ExpertMaterial();
        material.setMaterialId(13L);
        material.setMaterialCategory("IDENTITY");
        when(platformMapper.selectMaterialById(13L)).thenReturn(material);
        when(platformMapper.selectLatestMaterialByUserIdAndCategory(12L, "IDENTITY"))
                .thenReturn(material);
        ExpertIngestPersistenceService.PersistCommand command =
                new ExpertIngestPersistenceService.PersistCommand(
                        13L, "current", 12L, "REVIEW_PENDING", "PENDING",
                        1, 0, "done", false,
                        List.of(new ExpertIngestPersistenceService.FieldMappingDraft(
                                "real_name", "姓名", "李四", 0.9, "正文", "0")),
                        null, null, null);

        persistenceService.persist(command);

        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).selectProfileByUserIdForUpdate(12L);
        order.verify(platformMapper).updateMaterialJob(any(), any(), any(), any(),
                any(), any(), any());
        order.verify(platformMapper).deleteFieldMappings(13L);
        order.verify(platformMapper).insertFieldMapping(13L, "real_name", "姓名",
                "李四", 0.9, "正文", "0");
        order.verify(platformMapper).selectMaterialById(13L);
        order.verify(platformMapper).selectLatestMaterialByUserIdAndCategory(12L, "IDENTITY");
        order.verify(platformMapper).markProfilePendingIfNotApproved(12L);
        verify(dimensionAggregationService).recordAndAggregate(profile, 13L, null, false);
    }

    @Test
    void oldMaterialVersionCannotReplaceCurrentDraftGraph()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(14L);
        when(platformMapper.selectProfileByUserIdForUpdate(14L)).thenReturn(profile);
        when(platformMapper.updateMaterialJob(15L, "old", "REVIEW_PENDING", "PENDING",
                0, 1, "old result")).thenReturn(1);
        ExpertMaterial material = new ExpertMaterial();
        material.setMaterialId(15L);
        material.setMaterialCategory("IDENTITY");
        ExpertMaterial latest = new ExpertMaterial();
        latest.setMaterialId(16L);
        latest.setMaterialCategory("IDENTITY");
        when(platformMapper.selectMaterialById(15L)).thenReturn(material);
        when(platformMapper.selectLatestMaterialByUserIdAndCategory(14L, "IDENTITY"))
                .thenReturn(latest);
        ExpertIngestPersistenceService.PersistCommand command =
                new ExpertIngestPersistenceService.PersistCommand(
                        15L, "old", 14L, "REVIEW_PENDING", "PENDING",
                        0, 1, "old result", false, List.of(), null, null, null);

        persistenceService.persist(command);

        verify(platformMapper, never()).deleteDraftGraph(14L);
        verify(platformMapper, never()).markProfilePendingIfNotApproved(14L);
    }

    @Test
    void missingModelScoresDoesNotCreatePlaceholderScores()
    {
        ExpertProfile profile = readyProfile(16L);
        ExpertMaterial material = latestMaterial(17L, 16L);
        when(platformMapper.selectProfileByUserIdForUpdate(16L)).thenReturn(profile);
        when(platformMapper.updateMaterialJob(17L, "current", "REVIEW_PENDING", "PENDING",
                0, 1, "no evidence")).thenReturn(1);
        when(platformMapper.selectMaterialById(17L)).thenReturn(material);
        when(platformMapper.selectLatestMaterialByUserIdAndCategory(16L, "IDENTITY"))
                .thenReturn(material);
        persistenceService.persist(new ExpertIngestPersistenceService.PersistCommand(
                17L, "current", 16L, "REVIEW_PENDING", "PENDING",
                0, 1, "no evidence", false, List.of(), null, null, null));

        verify(dimensionAggregationService).recordAndAggregate(profile, 17L, null, false);
    }

    @Test
    void modelScoreWithEvidenceIsPersistedAsEvidenceScore()
    {
        ExpertProfile profile = readyProfile(18L);
        ExpertMaterial material = latestMaterial(19L, 18L);
        JSONObject analysis = JSON.parseObject("""
                {
                  "scores": {
                    "TECH_RISK_IDENT": {
                      "score": 81,
                      "confidence": 0.9,
                      "evidence": "项目材料第2页"
                    }
                  }
                }
                """);
        when(platformMapper.selectProfileByUserIdForUpdate(18L)).thenReturn(profile);
        when(platformMapper.updateMaterialJob(19L, "current", "REVIEW_PENDING", "PENDING",
                0, 0, "scored")).thenReturn(1);
        when(platformMapper.selectMaterialById(19L)).thenReturn(material);
        when(platformMapper.selectLatestMaterialByUserIdAndCategory(18L, "IDENTITY"))
                .thenReturn(material);
        persistenceService.persist(new ExpertIngestPersistenceService.PersistCommand(
                19L, "current", 18L, "REVIEW_PENDING", "PENDING",
                0, 0, "scored", false, List.of(), analysis, null, null));

        verify(dimensionAggregationService).recordAndAggregate(profile, 19L, analysis, false);
    }

    private static ExpertProfile readyProfile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setGraphVersion(0);
        return profile;
    }

    private static ExpertMaterial latestMaterial(Long materialId, Long userId)
    {
        ExpertMaterial material = new ExpertMaterial();
        material.setMaterialId(materialId);
        material.setUserId(userId);
        material.setMaterialCategory("IDENTITY");
        return material;
    }

}
