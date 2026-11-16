package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
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
                    "IND_CHEM_MATERIAL": {
                      "score": 70,
                      "confidence": 0.8,
                      "evidence": "无证据"
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
                analysis, java.util.Set.of("TECH_RISK_IDENT", "IND_CHEM_MATERIAL"));

        assertEquals(1, sanitized.getJSONObject("scores").size());
        assertEquals("Organization", sanitized.getJSONObject("graphFacts")
                .getJSONArray("entities").getJSONObject(0).getString("entityType"));
        assertEquals("ServiceRegion", sanitized.getJSONObject("graphFacts")
                .getJSONArray("entities").getJSONObject(1).getString("entityType"));
    }
}
