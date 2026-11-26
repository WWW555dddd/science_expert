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
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
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
        when(dimensionViewService.dimensions(12L, "DRAFT")).thenReturn(List.of());

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
        when(dimensionViewService.dimensions(21L, "DRAFT")).thenReturn(List.of());

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
    void temporaryUnavailablePeriodBlocksDateCandidate()
    {
        ExpertProfile profile = availableProfile(23L);
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(5);
        schedule.setServiceMode("BOTH");
        schedule.setWeeklyCapacity(2);
        ExpertUnavailablePeriod unavailable = new ExpertUnavailablePeriod();
        unavailable.setStartTime("2026-11-20 09:00:00");
        unavailable.setEndTime("2026-11-20 15:00:00");
        when(platformMapper.selectProfileByUserId(23L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(23L)).thenReturn(List.of(schedule));
        when(platformMapper.selectUnavailablePeriodsByUserId(23L))
                .thenReturn(List.of(unavailable));
        when(dimensionViewService.dimensions(23L, "DRAFT")).thenReturn(List.of());
        JSONObject arguments = new JSONObject();
        arguments.put("expertExternalKey", "expert:23");
        arguments.put("serviceDate", "2026-11-20");

        JSONObject result = tools.execute("expert_check_availability", arguments);

        assertTrue(!result.getBooleanValue("hardGateEligible"));
        assertTrue(result.getBooleanValue("temporarilyUnavailable"));
        assertTrue(result.getJSONArray("gateChecks").toJavaList(JSONObject.class).stream()
                .anyMatch(item -> "TEMPORARY_UNAVAILABILITY".equals(item.getString("code"))
                        && !item.getBooleanValue("passed")));
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
        when(dimensionViewService.dimensions(31L, "DRAFT")).thenReturn(List.of());
        when(dimensionViewService.dimensions(32L, "DRAFT")).thenReturn(List.of());
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
    void ranksUsingProfileDimensionsRelevantToTheTask()
    {
        ExpertProfile profile = availableProfile(41L);
        ExpertSchedule schedule = new ExpertSchedule();
        schedule.setWeekday(5);
        schedule.setServiceMode("BOTH");
        schedule.setWeeklyCapacity(2);
        when(platformMapper.selectProfileByUserId(41L)).thenReturn(profile);
        when(platformMapper.selectScheduleByUserId(41L)).thenReturn(List.of(schedule));
        ExpertGraphDimension risk = dimension("TECH_RISK_IDENT", "风险识别", 92, 0.9);
        ExpertGraphDimension fee = dimension("SERVICE_COST", "费用合理性", 40, 0.5);
        when(dimensionViewService.dimensions(41L, "DRAFT"))
                .thenReturn(List.of(risk, fee));
        JSONObject arguments = new JSONObject();
        arguments.put("expertExternalKey", "expert:41");
        arguments.put("serviceDate", "2026-11-20");
        arguments.put("matchCount", 2);
        arguments.put("profileTerms", List.of("风险识别"));

        JSONObject result = tools.execute("expert_check_availability", arguments);

        assertEquals("MATCHED_DIMENSIONS", result.getString("profileScoreScope"));
        assertEquals(92.0, result.getDoubleValue("profileScore"));
        assertEquals("TECH_RISK_IDENT", result.getJSONArray("profileDimensionMatches")
                .getJSONObject(0).getString("dimensionCode"));
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
        assertTrue(statement.getValue().contains("split(expert.externalKey, ':')[1]"));
        assertTrue(statement.getValue().contains(
                "(industry:KGNode {entityType: 'Industry'})"));
        assertTrue(statement.getValue().contains("ir.relationType = 'HAS_EXPERT'"));
    }

    private static ExpertProfile availableProfile(Long userId)
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setUserId(userId);
        profile.setReviewStatus("APPROVED");
        profile.setServiceStatus("AVAILABLE");
        return profile;
    }

    private static ExpertGraphDimension dimension(String code, String name,
            double score, double confidence)
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode(code);
        dimension.setDimensionName(name);
        dimension.setDescription(name);
        dimension.setScore(java.math.BigDecimal.valueOf(score));
        dimension.setConfidence(java.math.BigDecimal.valueOf(confidence));
        dimension.setDataStatus("AVAILABLE");
        return dimension;
    }
}
