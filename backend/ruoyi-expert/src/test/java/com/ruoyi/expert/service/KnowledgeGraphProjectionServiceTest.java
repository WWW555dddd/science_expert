package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphProjectionServiceTest
{
    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private KnowledgeGraphMapper graphMapper;

    @Mock
    private KnowledgeGraphOutboxService outboxService;

    @Spy
    private KnowledgeGraphOntologyService ontologyService;

    @InjectMocks
    private KnowledgeGraphProjectionService projectionService;

    @Test
    void projectsProfileRelationsWithoutUsingLegacyDimensionScores()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setProfileId(5L);
        profile.setUserId(12L);
        profile.setUserName("expert12");
        profile.setRealName("李专家");
        profile.setOrganization("某研究院");
        profile.setProfessionalTitle("高级工程师");
        profile.setResearchField("新材料");
        profile.setIndustryField("先进制造");
        profile.setServiceRegion("上海");
        profile.setReviewStatus("APPROVED");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(graphMapper.selectOntologyVersion(KnowledgeGraphOntologyService.ONTOLOGY_VERSION))
                .thenReturn(Map.of("versionCode", KnowledgeGraphOntologyService.ONTOLOGY_VERSION));

        Map<String, Long> ids = new ConcurrentHashMap<>();
        AtomicLong nextId = new AtomicLong(0);
        when(graphMapper.selectEntityIdByExternalKey(anyString(), anyString()))
                .thenAnswer(invocation -> ids.computeIfAbsent(
                        invocation.getArgument(0) + "|" + invocation.getArgument(1),
                        ignored -> nextId.incrementAndGet()));
        when(graphMapper.selectRelationId(anyLong(), anyString(), anyLong()))
                .thenReturn(101L, 102L, 103L, 104L, 105L);

        Map<String, Object> result = projectionService.projectExpert(12L);

        assertEquals("PROJECTED", result.get("status"));
        assertEquals(5, result.get("relationCount"));
        verify(graphMapper).expireProfileRelations(1L);
        verify(graphMapper).expireIndustryExpertRelations(1L);
        verify(graphMapper, times(5)).upsertRelation(anyMap());
        verify(graphMapper, times(5)).upsertEvidence(anyMap());
        verify(outboxService).enqueue(eq("EXPERT_PROJECTED"), eq("EXPERT"), eq(12L), anyMap());
        verify(platformMapper, never()).selectGraphDimensions(anyLong(), anyString());
    }
}
