package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertWorkspaceServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertIngestService ingestService;

    @InjectMocks
    private ExpertWorkspaceService workspaceService;

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
}
