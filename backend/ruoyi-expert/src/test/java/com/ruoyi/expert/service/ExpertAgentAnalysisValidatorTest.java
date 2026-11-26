package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;

class ExpertAgentAnalysisValidatorTest
{
    @Test
    void acceptsStrictEvidenceBackedPayload()
    {
        String payload = """
                {
                  "fields": [
                    {
                      "dbField": "real_name",
                      "fileField": "姓名",
                      "value": "张三",
                      "confidence": 0.95,
                      "evidence": "第1页姓名栏",
                      "conflict": false
                    }
                  ],
                  "issues": [],
                  "scores": {
                    "TECH_RISK_IDENT": {
                      "score": 82,
                      "confidence": 0.88,
                      "evidence": "第2页项目经历"
                    }
                  }
                }
                """;

        assertDoesNotThrow(() -> ExpertAgentAnalysisValidator.validate(
                JSON.parseObject(payload), Set.of("TECH_RISK_IDENT")));
    }

    @Test
    void rejectsScoreWithoutEvidence()
    {
        String payload = """
                {
                  "fields": [],
                  "issues": [],
                  "scores": {
                    "TECH_RISK_IDENT": {
                      "score": 82,
                      "confidence": 0.88,
                      "evidence": "无证据"
                    }
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class, () ->
                ExpertAgentAnalysisValidator.validate(
                        JSON.parseObject(payload), Set.of("TECH_RISK_IDENT")));
    }

    @Test
    void rejectsAbsenceAndSpeculationAsScoringEvidence()
    {
        String payload = """
                {
                  "fields": [],
                  "issues": [],
                  "scores": {
                    "SCENE_CLAIM_INSPECTION": {
                      "score": 60,
                      "confidence": 0.5,
                      "evidence": "文档未提及出险查勘，但作为教授可能具备相关能力"
                    }
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class, () ->
                ExpertAgentAnalysisValidator.validate(
                        JSON.parseObject(payload), Set.of("SCENE_CLAIM_INSPECTION")));
    }

    @Test
    void rejectsUnknownDimensionAndMissingRootSection()
    {
        String unknownDimension = """
                {
                  "fields": [],
                  "issues": [],
                  "scores": {
                    "UNKNOWN": {
                      "score": 82,
                      "confidence": 0.88,
                      "evidence": "第2页"
                    }
                  }
                }
                """;
        String missingIssues = """
                {"fields": [], "scores": {}}
                """;

        assertThrows(IllegalArgumentException.class, () ->
                ExpertAgentAnalysisValidator.validate(
                        JSON.parseObject(unknownDimension), Set.of("TECH_RISK_IDENT")));
        assertThrows(IllegalArgumentException.class, () ->
                ExpertAgentAnalysisValidator.validate(
                        JSON.parseObject(missingIssues), Set.of("TECH_RISK_IDENT")));
    }

    @Test
    void acceptsEvidenceBackedMaterialGraphFacts()
    {
        String payload = """
                {
                  "fields": [],
                  "issues": [],
                  "scores": {},
                  "graphFacts": {
                    "entities": [
                      {
                        "entityType": "Project",
                        "name": "智能制造项目",
                        "confidence": 0.9,
                        "evidence": "第3页：承担智能制造项目"
                      }
                    ],
                    "relations": [
                      {
                        "sourceType": "Expert",
                        "sourceName": "李专家",
                        "relationType": "PARTICIPATED_IN",
                        "targetType": "Project",
                        "targetName": "智能制造项目",
                        "confidence": 0.9,
                        "evidence": "第3页：承担智能制造项目"
                      }
                    ]
                  }
                }
                """;

        assertDoesNotThrow(() -> ExpertAgentAnalysisValidator.validate(
                JSON.parseObject(payload), Set.of("TECH_RISK_IDENT")));
    }
}
