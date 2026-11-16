package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.config.ExpertAgentProperties;
import com.ruoyi.expert.mapper.AgentRuntimeMapper;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphAgentRuntimeServiceTest
{
    @Mock
    private AgentRuntimeMapper runMapper;

    @Mock
    private KnowledgeGraphAgentTools tools;

    @Mock
    private Neo4jHttpClient neo4j;

    @Mock
    private ExpertAgentProperties properties;

    @Mock
    private KnowledgeGraphTaskService taskGraphService;

    @InjectMocks
    private KnowledgeGraphAgentRuntimeService runtimeService;

    @Test
    void fallbackRunsBoundedToolLoopAndPersistsEveryStep()
    {
        when(neo4j.isEnabled()).thenReturn(true);
        when(properties.isRemoteConfigured()).thenReturn(false);
        when(tools.suggestTerms(anyString())).thenReturn(List.of("新材料"));
        when(taskGraphService.createTask(anyString(), anyString(), any(), any(), any(),
                any(), any(), anyList())).thenReturn(Map.of(
                        "taskEntityId", 99L,
                        "requiredCapabilityCount", 1));
        when(tools.execute(anyString(), any(JSONObject.class))).thenAnswer(invocation -> {
            String name = invocation.getArgument(0);
            return switch (name)
            {
                case "kg_search_experts" -> searchResult();
                case "kg_find_paths" -> pathResult();
                case "expert_filter_available" -> filterResult();
                case "kg_expand_evidence" -> new JSONObject();
                default -> new JSONObject();
            };
        });

        Map<String, Object> result = runtimeService.run(
                "为新材料项目寻找上海地区已认证专家", 6, "tester");

        assertEquals("COMPLETED", result.get("status"));
        assertEquals(5, result.get("stepCount"));
        assertTrue(String.valueOf(result.get("finalAnswer")).contains("李专家"));
        verify(runMapper).insertRun(anyMap());
        verify(runMapper, times(5)).insertStep(anyMap());
        verify(runMapper).updateRun(anyString(), anyString(), anyInt(),
                anyString(), any());
    }

    private static JSONObject searchResult()
    {
        JSONArray candidates = new JSONArray();
        candidates.add(candidate("expert:12", "李专家"));
        candidates.add(candidate("expert:13", "王专家"));
        JSONObject result = new JSONObject();
        result.put("candidates", candidates);
        return result;
    }

    private static JSONObject candidate(String key, String name)
    {
        JSONObject candidate = new JSONObject();
        candidate.put("externalKey", key);
        candidate.put("expertName", name);
        candidate.put("matchCount", 2);
        return candidate;
    }

    private static JSONObject pathResult()
    {
        JSONObject relation = new JSONObject();
        relation.put("relationId", 100);
        relation.put("relationType", "EXPERTISE_IN");
        JSONObject path = new JSONObject();
        path.put("relations", new JSONArray(List.of(relation)));
        JSONObject result = new JSONObject();
        result.put("paths", new JSONArray(List.of(path)));
        result.put("pathCount", 1);
        return result;
    }

    private static JSONObject filterResult()
    {
        JSONObject first = availabilityResult("expert:12", 82);
        JSONObject second = availabilityResult("expert:13", 78);
        JSONObject result = new JSONObject();
        result.put("candidates", new JSONArray(List.of(first, second)));
        result.put("eligibleCount", 2);
        return result;
    }

    private static JSONObject availabilityResult(String key, double rankingScore)
    {
        JSONObject result = new JSONObject();
        result.put("expertExternalKey", key);
        result.put("reviewStatus", "APPROVED");
        result.put("serviceStatus", "AVAILABLE");
        result.put("hardGateEligible", true);
        result.put("rankingScore", rankingScore);
        result.put("profileScore", 75);
        result.put("profileCoverage", 90);
        return result;
    }
}
