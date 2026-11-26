package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertBusinessMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class ExpertWorkspaceServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertIngestService ingestService;

    @Mock
    private ExpertCertificationService certificationService;

    @Mock
    private KnowledgeGraphProjectionService graphProjectionService;

    @Mock
    private KnowledgeGraphOutboxService graphOutboxService;

    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Mock
    private ExpertObjectCleanupService objectCleanupService;

    @Mock
    private ExpertBusinessMapper businessMapper;

    @InjectMocks
    private ExpertWorkspaceService workspaceService;

    @Test
    void returnsLatestTaskOccupancyForPersonalCalendar()
    {
        Map<String, Object> free = Map.of(
                "contextId", 1L,
                "externalTaskId", "TASK-001",
                "serviceStart", "2026-12-07 07:30:00",
                "serviceEnd", "2026-12-07 09:30:00",
                "occupancyStatus", "FREE");
        Map<String, Object> occupied = Map.of(
                "contextId", 2L,
                "externalTaskId", "TASK-001",
                "serviceStart", "2026-12-07 07:30:00",
                "serviceEnd", "2026-12-07 09:30:00",
                "occupancyStatus", "OCCUPIED");
        when(businessMapper.selectOccupancySnapshots(
                5L, "2026-12-01 00:00:00", "2026-12-31 23:59:59"))
                .thenReturn(List.of(free, occupied));

        List<Map<String, Object>> result = workspaceService.getOccupancyPeriods(
                5L, "2026-12-01 00:00:00", "2026-12-31 23:59:59");

        assertEquals(1, result.size());
        assertEquals("OCCUPIED", result.get(0).get("occupancyStatus"));
    }

    @Test
    void firstMaterialUploadCreatesVersionOne()
    {
        ExpertProfile profile = profile(5L);
        ExpertMaterial request = upload("identity-v1.pdf", "minio://expert/experts/5/identity-v1.pdf");
        request.setMaterialCategory("IDENTITY");
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.selectCertificationStatus(5L))
                .thenReturn(List.of(Map.of("categoryCode", "IDENTITY")));
        when(platformMapper.selectLatestMaterialByUserIdAndCategoryForUpdate(5L, "IDENTITY"))
                .thenReturn(null);
        when(platformMapper.insertMaterial(any(ExpertMaterial.class))).thenAnswer(invocation -> {
            ExpertMaterial material = invocation.getArgument(0);
            material.setMaterialId(101L);
            return 1;
        });
        when(platformMapper.insertMaterialFile(any(ExpertMaterialFile.class))).thenAnswer(invocation -> {
            ExpertMaterialFile file = invocation.getArgument(0);
            file.setFileId(201L);
            return 1;
        });
        when(certificationService.profileStatusAfterReview(5L)).thenReturn("PENDING_REVIEW");
        when(platformMapper.selectMaterialById(101L)).thenReturn(request);

        workspaceService.submitMaterial(request, 5L, "expert-a");

        ArgumentCaptor<ExpertMaterial> captor = ArgumentCaptor.forClass(ExpertMaterial.class);
        verify(platformMapper).insertMaterial(captor.capture());
        assertEquals(1, captor.getValue().getVersionNo());
        verify(platformMapper).insertMaterialFile(any(ExpertMaterialFile.class));
        verify(platformMapper).prepareMaterialForReview(101L, "expert-a");
        verify(ingestService).enqueueAfterCommit(101L);
    }

    @Test
    void existingMaterialUploadAddsFileWithoutCreatingNewVersion()
    {
        ExpertProfile profile = profile(6L);
        ExpertMaterial current = material(6L, 93L);
        current.setMaterialCategory("IDENTITY");
        current.setVersionNo(3);
        current.setFilePath("minio://expert/experts/6/identity-v3.pdf");
        ExpertMaterial request = upload("identity-v4.pdf", "minio://expert/experts/6/identity-v4.pdf");
        request.setMaterialCategory("IDENTITY");
        request.setMaterialId(93L);
        when(platformMapper.selectProfileByUserIdForUpdate(6L)).thenReturn(profile);
        when(platformMapper.selectCertificationStatus(6L))
                .thenReturn(List.of(Map.of("categoryCode", "IDENTITY")));
        when(platformMapper.selectLatestMaterialByUserIdAndCategoryForUpdate(6L, "IDENTITY"))
                .thenReturn(current);
        when(platformMapper.insertMaterialFile(any(ExpertMaterialFile.class))).thenReturn(1);
        when(certificationService.profileStatusAfterReview(6L)).thenReturn("PENDING_REVIEW");
        when(platformMapper.selectMaterialById(93L)).thenReturn(request);

        workspaceService.submitMaterial(request, 6L, "expert-a");

        ArgumentCaptor<ExpertMaterialFile> captor = ArgumentCaptor.forClass(ExpertMaterialFile.class);
        verify(platformMapper).insertMaterialFile(captor.capture());
        assertEquals(93L, captor.getValue().getMaterialId());
        assertEquals("identity-v4.pdf", captor.getValue().getFileName());
        verify(platformMapper, never()).insertMaterial(any());
        verify(platformMapper).prepareMaterialForReview(93L, "expert-a");
        verify(platformMapper).deleteFieldMappings(93L);
        verify(platformMapper).deleteDimensionAssessmentsByMaterial(93L);
        verify(graphMapper).deleteMaterialGraphRelations(93L);
        verify(graphMapper).deleteMaterialGraphEvidence(93L);
        verify(ingestService).enqueueAfterCommit(93L);
    }

    @Test
    void addingVersionFileKeepsMaterialVersionUnchangedAndMarksPendingSubmit()
    {
        ExpertProfile profile = profile(7L);
        ExpertMaterial material = material(7L, 94L);
        material.setMaterialCategory("IDENTITY");
        material.setVersionNo(2);
        ExpertMaterialFile file = versionFile("identity-extra.pdf",
                "minio://expert/experts/7/identity-extra.pdf");
        when(platformMapper.selectProfileByUserIdForUpdate(7L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(94L)).thenReturn(material);
        when(platformMapper.insertMaterialFile(any(ExpertMaterialFile.class))).thenAnswer(invocation -> {
            ExpertMaterialFile saved = invocation.getArgument(0);
            saved.setFileId(301L);
            return 1;
        });
        when(platformMapper.selectMaterialFile(94L, 301L)).thenReturn(file);

        workspaceService.addMaterialFile(94L, file, 7L, "expert-a");

        assertEquals(94L, file.getMaterialId());
        assertEquals(0, file.getSortNo());
        verify(platformMapper, never()).prepareMaterialForReview(any(), any());
    }

    @Test
    void deletingLastVersionFileIsRejected()
    {
        ExpertProfile profile = profile(8L);
        ExpertMaterial material = material(8L, 95L);
        ExpertMaterialFile file = versionFile("identity.pdf",
                "minio://expert/experts/8/identity.pdf");
        file.setFileId(401L);
        when(platformMapper.selectProfileByUserIdForUpdate(8L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(95L)).thenReturn(material);
        when(platformMapper.selectMaterialFile(95L, 401L)).thenReturn(file);
        when(platformMapper.countMaterialFiles(95L)).thenReturn(1);

        assertThrows(ServiceException.class,
                () -> workspaceService.deleteMaterialFile(95L, 401L, 8L, "expert-a"));

        verify(platformMapper, never()).deleteMaterialFile(any(), any());
    }

    @Test
    void savingVersionFilesSubmitsSameMaterialVersionForReview()
    {
        ExpertProfile profile = profile(9L);
        ExpertMaterial material = material(9L, 96L);
        material.setMaterialCategory("ACHIEVEMENT");
        material.setVersionNo(4);
        ExpertMaterialFile first = versionFile("proof-a.pdf",
                "minio://expert/experts/9/proof-a.pdf");
        first.setFileId(501L);
        ExpertMaterialFile second = versionFile("proof-b.pdf",
                "minio://expert/experts/9/proof-b.pdf");
        second.setFileId(502L);
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(platformMapper.selectMaterialByIdForUpdate(96L)).thenReturn(material);
        when(platformMapper.selectMaterialFiles(96L)).thenReturn(List.of(first, second));
        when(certificationService.profileStatusAfterReview(9L)).thenReturn("PENDING_REVIEW");

        workspaceService.submitMaterialRevision(96L, 9L, "expert-a");

        verify(platformMapper).syncMaterialPrimaryFile(96L, first, "expert-a");
        verify(platformMapper).prepareMaterialForReview(96L, "expert-a");
        verify(platformMapper, never()).insertMaterial(any());
        verify(ingestService).enqueueAfterCommit(96L);
        assertEquals(4, material.getVersionNo());
    }

    @Test
    void manualQualificationValidityUpdateIsRejected()
    {
        ExpertMaterial request = new ExpertMaterial();

        assertThrows(ServiceException.class,
                () -> workspaceService.updateMaterialValidity(97L, request, 10L, "expert-a"));
    }

    @Test
    void retryLocksProfileBeforeResettingTask()
    {
        ExpertMaterial material = material(5L, 21L);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(5L);
        when(platformMapper.selectMaterialById(21L)).thenReturn(material);
        when(platformMapper.selectProfileByUserIdForUpdate(5L)).thenReturn(profile);
        when(platformMapper.resetMaterialJob(21L)).thenReturn(1);

        workspaceService.retryMaterial(21L, 5L);

        InOrder order = inOrder(platformMapper, ingestService);
        order.verify(platformMapper).selectProfileByUserIdForUpdate(5L);
        order.verify(platformMapper).resetMaterialJob(21L);
        order.verify(ingestService).enqueueAfterCommit(21L);
    }

    @Test
    void duplicateRetryDoesNotEnqueueAgain()
    {
        ExpertMaterial material = material(6L, 22L);
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(6L);
        when(platformMapper.selectMaterialById(22L)).thenReturn(material);
        when(platformMapper.selectProfileByUserIdForUpdate(6L)).thenReturn(profile);
        when(platformMapper.resetMaterialJob(22L)).thenReturn(0);

        assertThrows(ServiceException.class, () -> workspaceService.retryMaterial(22L, 6L));

        verify(ingestService, never()).enqueueAfterCommit(22L);
    }

    private static ExpertMaterial material(Long userId, Long materialId)
    {
        ExpertMaterial material = new ExpertMaterial();
        material.setUserId(userId);
        material.setMaterialId(materialId);
        return material;
    }

    private static ExpertMaterial upload(String fileName, String filePath)
    {
        ExpertMaterial material = new ExpertMaterial();
        material.setFileName(fileName);
        material.setFilePath(filePath);
        material.setFileSize(1024L);
        return material;
    }

    private static ExpertMaterialFile versionFile(String fileName, String filePath)
    {
        ExpertMaterialFile file = new ExpertMaterialFile();
        file.setFileName(fileName);
        file.setFilePath(filePath);
        file.setFileSize(1024L);
        return file;
    }

    private static ExpertProfile profile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setReviewStatus("APPROVED");
        return profile;
    }
}
