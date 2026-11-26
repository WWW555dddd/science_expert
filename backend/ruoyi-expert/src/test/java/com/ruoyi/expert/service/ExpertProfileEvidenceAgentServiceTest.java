package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.expert.domain.ExpertGraphDimension;

class ExpertProfileEvidenceAgentServiceTest
{
    @Test
    void validatesAgentOutputAndLimitsSingleAdjustment()
    {
        ExpertGraphDimension dimension = new ExpertGraphDimension();
        dimension.setDimensionCode("PERF_REPORT_QUALITY");
        dimension.setDimensionName("历史报告质量");
        dimension.setScore(BigDecimal.valueOf(80));
        dimension.setDataStatus("AVAILABLE");
        String payload = """
                {
                  "summary": "服务报告支持上调报告质量",
                  "confidence": 0.88,
                  "items": [
                    {
                      "dimensionCode": "PERF_REPORT_QUALITY",
                      "suggestedScore": 99,
                      "reason": "报告按期且结构完整",
                      "evidence": "报告按期完成，结构完整",
                      "confidence": 0.9
                    },
                    {
                      "dimensionCode": "UNKNOWN",
                      "suggestedScore": 70,
                      "reason": "非法维度",
                      "evidence": "测试",
                      "confidence": 0.5
                    }
                  ],
                  "issues": []
                }
                """;

        ExpertProfileEvidenceAgentService.Analysis analysis =
                ExpertProfileEvidenceAgentService.sanitize(
                        JSON.parseObject(payload),
                        Map.of("PERF_REPORT_QUALITY", dimension));

        assertEquals(1, analysis.items().size());
        assertEquals(90.0, analysis.items().get(0).suggestedScore());
        assertEquals(0.9, analysis.items().get(0).confidence());
    }
}
