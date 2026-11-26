package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

class DeepSeekAgentAnalysisSanitizerTest
{
    @Test
    void keepsValidScoresAndCanonicalizesKnownEntityAliases()
    {
        JSONObject analysis = JSON.parseObject("""
                {
                  "fields": [],
                  "issues": [],
                  "scores": {
                    "TECH_RISK_IDENT": {
                      "score": 82,
                      "confidence": 0.9,
                      "evidence": "第2页项目经历"
                    },
                    "ADAPT_METHOD_REUSE": {
                      "score": 70,
                      "confidence": 0.8,
                      "evidence": "无证据"
                    },
                    "SCENE_CLAIM_INSPECTION": {
                      "score": 60,
                      "confidence": 0.5,
                      "evidence": "文档未提及出险查勘，但可能具备相关能力"
                    }
                  },
                  "graphFacts": {
                    "entities": [
                      {"entityType":"University","name":"南昌大学","confidence":0.9,
                       "evidence":"第1页单位"},
                      {"entityType":"Region","name":"江西省","confidence":0.9,
                       "evidence":"第1页地区"}
                    ],
                    "relations": []
                  }
                }
                """);

        JSONObject sanitized = DeepSeekAgentService.sanitizeAnalysis(
                analysis, java.util.Set.of("TECH_RISK_IDENT", "ADAPT_METHOD_REUSE",
                        "SCENE_CLAIM_INSPECTION"));

        assertEquals(1, sanitized.getJSONObject("scores").size());
        assertEquals("Organization", sanitized.getJSONObject("graphFacts")
                .getJSONArray("entities").getJSONObject(0).getString("entityType"));
        assertEquals("ServiceRegion", sanitized.getJSONObject("graphFacts")
                .getJSONArray("entities").getJSONObject(1).getString("entityType"));
    }

    @Test
    void truncatesOversizedGraphFactsInsteadOfFailingTheWholeAnalysis()
    {
        JSONObject analysis = new JSONObject();
        analysis.put("fields", new JSONArray());
        analysis.put("issues", new JSONArray());
        analysis.put("scores", new JSONObject());
        JSONObject facts = new JSONObject();
        JSONArray entities = new JSONArray();
        JSONArray relations = new JSONArray();
        for (int index = 0; index < 13; index++)
        {
            String name = "项目" + index;
            JSONObject entity = new JSONObject();
            entity.put("entityType", "Project");
            entity.put("name", name);
            entity.put("confidence", 0.9);
            entity.put("evidence", "项目：" + name);
            entities.add(entity);

            JSONObject relation = new JSONObject();
            relation.put("sourceType", "Expert");
            relation.put("sourceName", "李专家");
            relation.put("relationType", "PARTICIPATED_IN");
            relation.put("targetType", "Project");
            relation.put("targetName", name);
            relation.put("confidence", 0.9);
            relation.put("evidence", "项目：" + name);
            relations.add(relation);
        }
        facts.put("entities", entities);
        facts.put("relations", relations);
        analysis.put("graphFacts", facts);

        JSONObject sanitized = DeepSeekAgentService.sanitizeAnalysis(
                analysis, java.util.Set.of());

        assertEquals(12, sanitized.getJSONObject("graphFacts")
                .getJSONArray("entities").size());
        assertEquals(12, sanitized.getJSONObject("graphFacts")
                .getJSONArray("relations").size());
    }
}
