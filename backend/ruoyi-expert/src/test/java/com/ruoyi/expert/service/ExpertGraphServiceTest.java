package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class ExpertGraphServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertProfileDimensionAggregationService dimensionAggregationService;

    @InjectMocks
    private ExpertGraphService graphService;

    @Test
    void publishDraftLocksProfileBeforeInspectingDraft()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(7L);
        when(platformMapper.selectProfileByUserIdForUpdate(7L)).thenReturn(profile);
        when(platformMapper.countDraftGraph(7L)).thenReturn(30);
        when(platformMapper.incrementGraphVersion(7L)).thenReturn(1);
        when(platformMapper.approveDraftGraph(7L)).thenReturn(30);

        assertTrue(graphService.publishDraft(7L));

        InOrder order = inOrder(platformMapper);
        order.verify(platformMapper).selectProfileByUserIdForUpdate(7L);
        verify(dimensionAggregationService, times(2)).rebuildDraft(profile, true);
        order.verify(platformMapper).countDraftGraph(7L);
        order.verify(platformMapper).incrementGraphVersion(7L);
        order.verify(platformMapper).deleteApprovedGraph(7L);
        order.verify(platformMapper).approveDraftGraph(7L);
        verify(platformMapper).deleteApprovedDimensionEvidence(7L);
    }

    @Test
    void publishDraftReturnsWithoutChangingVersionWhenDraftIsGone()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(8L);
        when(platformMapper.selectProfileByUserIdForUpdate(8L)).thenReturn(profile);
        when(platformMapper.countDraftGraph(8L)).thenReturn(0);

        assertFalse(graphService.publishDraft(8L));

        verify(platformMapper, never()).incrementGraphVersion(8L);
        verify(platformMapper, never()).deleteApprovedGraph(8L);
    }

    @Test
    void incompleteEvidencePublishesPartialProfile()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(9L);
        when(platformMapper.selectProfileByUserIdForUpdate(9L)).thenReturn(profile);
        when(platformMapper.countDraftGraph(9L)).thenReturn(29);
        when(platformMapper.incrementGraphVersion(9L)).thenReturn(1);
        when(platformMapper.approveDraftGraph(9L)).thenReturn(29);

        assertTrue(graphService.publishDraft(9L));

        verify(platformMapper).incrementGraphVersion(9L);
        verify(platformMapper).approveDraftGraph(9L);
    }
}
