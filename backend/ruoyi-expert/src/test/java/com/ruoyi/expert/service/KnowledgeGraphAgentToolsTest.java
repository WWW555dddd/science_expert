package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphAgentToolsTest
{
    @Mock
    private Neo4jHttpClient neo4j;

    @Mock
    private KnowledgeGraphQueryService queryService;

    @Mock
    private ExpertPlatformMapper platformMapper;

    @Mock
    private ExpertDimensionViewService dimensionViewService;

    @InjectMocks
    private KnowledgeGraphAgentTools tools;

    @Test
    void exposesOnlyBoundedReadTools()
    {
        JSONArray definitions = tools.definitions();

        assertEquals(5, definitions.size());
        assertTrue(definitions.toJSONString().contains("kg_search_experts"));
        assertTrue(definitions.toJSONString().contains("kg_find_paths"));
        assertTrue(definitions.toJSONString().contains("kg_expand_evidence"));
        assertTrue(definitions.toJSONString().contains("expert_check_availability"));
        assertTrue(definitions.toJSONString().contains("expert_filter_available"));
    }

    @Test
    void checksAuthenticationAndScheduleStatus()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(12L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        when(platformMapper.selectProfileByUserId(12L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(12L)).thenReturn(List.of());
        when(dimensionViewService.dimensions(12L, "APPROVED")).thenReturn(List.of());

        JSONObject arguments = new JSONObject();
        arguments.put("expertExternalKey", "expert:12");
        JSONObject result = tools.execute("expert_check_availability", arguments);

        assertEquals("APPROVED", result.getString("reviewStatus"));
        assertEquals("AVAILABLE", result.getString("serviceStatus"));
        assertEquals("expert:12", result.getString("expertExternalKey"));
        assertTrue(!result.getBooleanValue("hardGateEligible"));
    }

    @Test
    void appliesDateRegionModeAndIndustryHardGate()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(21L);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        profile.setServiceRegion("江西省南昌市");
        profile.setIndustryField("新能源");
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(5);
        schedule.setServiceMode("BOTH");
        schedule.setWeeklyCapacity(2);
        schedule.setEffectiveFrom("2026-11-01");
        when(platformMapper.selectProfileByUserId(21L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(21L)).thenReturn(List.of(schedule));
        when(dimensionViewService.dimensions(21L, "APPROVED")).thenReturn(List.of());

        JSONObject arguments = new JSONObject();
        arguments.put("expertExternalKey", "expert:21");
        arguments.put("serviceDate", "2026-11-20");
        arguments.put("serviceRegion", "南昌");
        arguments.put("serviceMode", "ONSITE");
        arguments.put("industry", "新能源");
        arguments.put("matchCount", 3);
        JSONObject result = tools.execute("expert_check_availability", arguments);

        assertTrue(result.getBooleanValue("hardGateEligible"));
        assertEquals(3, result.getIntValue("graphMatchCount"));
        assertTrue(result.getDoubleValue("rankingScore") > 0);
    }

    @Test
    void batchFilterRemovesUnavailableCandidates()
    {
        ExpertProfile available = availableProfile(31L);
        ExpertProfile paused = availableProfile(32L);
        paused.setServiceStatus("PAUSED");
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(5);
        schedule.setServiceMode("ONSITE");
        schedule.setWeeklyCapacity(1);
        when(platformMapper.selectProfileByUserId(31L)).thenReturn(available);
        when(platformMapper.selectProfileByUserId(32L)).thenReturn(paused);
        when(platformMapper.selectScheduleByUserId(31L)).thenReturn(List.of(schedule));
        when(platformMapper.selectScheduleByUserId(32L)).thenReturn(List.of(schedule));
        when(dimensionViewService.dimensions(31L, "APPROVED")).thenReturn(List.of());
        when(dimensionViewService.dimensions(32L, "APPROVED")).thenReturn(List.of());
        JSONObject arguments = new JSONObject();
        arguments.put("expertExternalKeys", List.of("expert:31", "expert:32"));
        arguments.put("matchCounts", List.of(4, 5));
        arguments.put("serviceDate", "2026-11-20");
        arguments.put("serviceMode", "ONSITE");

        JSONObject result = tools.execute("expert_filter_available", arguments);

        assertEquals(1, result.getIntValue("eligibleCount"));
        assertEquals("expert:31", result.getJSONArray("candidates")
                .getJSONObject(0).getString("expertExternalKey"));
    }

    @Test
    void expandsEvidenceThroughMysqlSource()
    {
        when(queryService.evidenceForRelations(anyList()))
                .thenReturn(List.of(Map.of("sourceType", "MATERIAL")));
        when(queryService.evidenceForEntities(anyList())).thenReturn(List.of());
        JSONObject arguments = new JSONObject();
        arguments.put("relationIds", List.of(10));
        arguments.put("entityIds", List.of());

        JSONObject result = tools.execute("kg_expand_evidence", arguments);

        assertEquals(1, result.getJSONArray("relationEvidence").size());
    }

    @Test
    void acceptsJavaListTermsFromLocalFallback()
    {
        when(neo4j.query(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "expertEntityId", 1L,
                "externalKey", "expert:12",
                "expertName", "李专家",
                "matchCount", 1)));
        JSONObject arguments = new JSONObject();
        arguments.put("terms", List.of("人工智能"));

        JSONObject result = tools.execute("kg_search_experts", arguments);

        assertEquals(1, result.getIntValue("candidateCount"));
        ArgumentCaptor<String> statement = ArgumentCaptor.forClass(String.class);
        verify(neo4j).query(statement.capture(), anyMap());
        assertTrue(statement.getValue().contains("r.ownerExpertId ="));
        assertTrue(statement.getValue().contains("split(e.externalKey, ':')[1]"));
        assertTrue(statement.getValue().contains("node.entityType <> 'Expert'"));
    }

    private static ExpertProfile availableProfile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        return profile;
    }
}
