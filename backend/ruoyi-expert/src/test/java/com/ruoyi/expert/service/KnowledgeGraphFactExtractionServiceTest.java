package com.ruoyi.expert.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertProfile;

class KnowledgeGraphFactExtractionServiceTest
{
    private final KnowledgeGraphFactExtractionService service =
            new KnowledgeGraphFactExtractionService();

    @Test
    void extractsProjectAchievementAndIndustryFromLabeledLines()
    {
        ExpertProfile profile = new ExpertProfile();
        profile.setRealName("李专家");
        String text = """
                姓名：李专家
                项目：智能装备研发项目
                成果：发明专利A
                产业领域：高端制造
                项目：1项
                成果：其中近
                职称：教授 年
                """;

        JSONObject facts = service.resolveFacts(null, profile, text);

        assertEquals(4, facts.getJSONArray("entities").size());
        assertEquals(4, facts.getJSONArray("relations").size());
        assertTrue(facts.getJSONArray("entities").getJSONObject(0)
                .containsKey("charStart"));
    }
}
