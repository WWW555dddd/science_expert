package com.ruoyi.expert.service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertProfile;

/**
 * 从材料抽取候选图谱事实。远程模型结果优先，本地规则作为兼容降级。
 */
@Service
public class KnowledgeGraphFactExtractionService
{
    private static final Map<String, Rule> RULES = rules();

    public JSONObject resolveFacts(JSONObject remoteAnalysis, ExpertProfile profile,
            String documentText)
    {
        JSONObject remoteFacts = remoteAnalysis == null
                ? null : remoteAnalysis.getJSONObject("graphFacts");
        JSONObject facts = remoteFacts == null
                ? extractWithRules(profile, documentText) : remoteFacts;
        attachEvidenceOffsets(facts, documentText);
        return facts;
    }

    public JSONObject emptyFacts()
    {
        JSONObject result = new JSONObject();
        result.put("entities", new JSONArray());
        result.put("relations", new JSONArray());
        return result;
    }

    private JSONObject extractWithRules(ExpertProfile profile, String documentText)
    {
        JSONObject result = emptyFacts();
        if (StringUtils.isEmpty(documentText))
        {
            return result;
        }
        JSONArray entities = result.getJSONArray("entities");
        JSONArray relations = result.getJSONArray("relations");
        Set<String> entityKeys = new LinkedHashSet<>();
        Set<String> relationKeys = new LinkedHashSet<>();
        String sourceName = StringUtils.isEmpty(profile.getRealName())
                ? "专家" : profile.getRealName();

        for (String rawLine : documentText.split("\\R"))
        {
            String line = rawLine.trim();
            if (line.length() < 3)
            {
                continue;
            }
            for (Map.Entry<String, Rule> entry : RULES.entrySet())
            {
                String value = extractValue(line, entry.getKey());
                if (StringUtils.isEmpty(value))
                {
                    continue;
                }
                Rule rule = entry.getValue();
                for (String candidate : splitValues(value))
                {
                    String name = normalizeCandidate(candidate);
                    if (name == null)
                    {
                        continue;
                    }
                    String entityKey = rule.entityType() + "|" + name;
                    if (entityKeys.add(entityKey))
                    {
                        JSONObject entity = new JSONObject();
                        entity.put("entityType", rule.entityType());
                        entity.put("name", name);
                        entity.put("confidence", 0.70);
                        entity.put("evidence", abbreviate(line, 500));
                        entities.add(entity);
                    }
                    String relationKey = rule.relationType() + "|" + name;
                    if (relationKeys.add(relationKey))
                    {
                        JSONObject relation = new JSONObject();
                        relation.put("sourceType", "Expert");
                        relation.put("sourceName", sourceName);
                        relation.put("relationType", rule.relationType());
                        relation.put("targetType", rule.entityType());
                        relation.put("targetName", name);
                        relation.put("confidence", 0.70);
                        relation.put("evidence", abbreviate(line, 500));
                        relations.add(relation);
                    }
                    if (entities.size() >= 20 || relations.size() >= 20)
                    {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private static void attachEvidenceOffsets(JSONObject facts, String documentText)
    {
        if (facts == null || StringUtils.isEmpty(documentText))
        {
            return;
        }
        attachOffsets(facts.getJSONArray("entities"), documentText);
        attachOffsets(facts.getJSONArray("relations"), documentText);
    }

    private static void attachOffsets(JSONArray items, String documentText)
    {
        if (items == null)
        {
            return;
        }
        for (int index = 0; index < items.size(); index++)
        {
            JSONObject item = items.getJSONObject(index);
            if (item == null)
            {
                continue;
            }
            String evidence = item.getString("evidence");
            if (StringUtils.isEmpty(evidence))
            {
                continue;
            }
            int start = documentText.indexOf(evidence);
            if (start >= 0)
            {
                item.put("charStart", start);
                item.put("charEnd", start + evidence.length());
            }
        }
    }

    private static String extractValue(String line, String label)
    {
        int labelIndex = line.indexOf(label);
        if (labelIndex < 0)
        {
            return null;
        }
        int colon = -1;
        for (char separator : new char[] {':', '：', '=', ' '})
        {
            int index = line.indexOf(separator, labelIndex + label.length());
            if (index >= 0 && (colon < 0 || index < colon))
            {
                colon = index;
            }
        }
        if (colon < 0)
        {
            return null;
        }
        String value = line.substring(colon + 1).trim();
        if (value.length() > 255)
        {
            value = value.substring(0, 255);
        }
        return value;
    }

    private static String normalizeCandidate(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String name = value.trim()
                .replaceAll("\\s+年.*$", "")
                .replaceAll("^(主要包括|主要为|研究方向为)\\s*[:：]?\\s*", "")
                .replaceAll("^[,，;；:：\\s]+|[,，;；:：\\s]+$", "");
        if (name.length() < 2 || name.length() > 80
                || name.matches("^[0-9]+(\\.[0-9]+)?\\s*(项|个|篇|余次|年)?$")
                || name.matches("^(其中近|共主持|申请国家|省市科技).*$")
                || name.matches("^(性别|国籍|姓名|职称|毕业院校|个人信息)\\s*[:：].*$")
                || name.contains("个人信息")
                || name.contains("姓名：")
                || name.contains("国籍：")
                || name.contains("毕业院校："))
        {
            return null;
        }
        return name;
    }

    private static List<String> splitValues(String value)
    {
        return List.of(value.split("[，,；;、|]")).stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .limit(3)
                .toList();
    }

    private static String abbreviate(String value, int maxLength)
    {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Map<String, Rule> rules()
    {
        Map<String, Rule> rules = new LinkedHashMap<>();
        rules.put("项目", new Rule("Project", "PARTICIPATED_IN"));
        rules.put("课题", new Rule("Project", "PARTICIPATED_IN"));
        rules.put("专利", new Rule("Achievement", "PRODUCED"));
        rules.put("标准", new Rule("Achievement", "PRODUCED"));
        rules.put("论文", new Rule("Achievement", "PRODUCED"));
        rules.put("软著", new Rule("Achievement", "PRODUCED"));
        rules.put("成果", new Rule("Achievement", "PRODUCED"));
        rules.put("技术", new Rule("Capability", "EXPERTISE_IN"));
        rules.put("专业方向", new Rule("Capability", "EXPERTISE_IN"));
        rules.put("研究领域", new Rule("Capability", "EXPERTISE_IN"));
        rules.put("产业方向", new Rule("Industry", "WORKS_IN_INDUSTRY"));
        rules.put("产业领域", new Rule("Industry", "WORKS_IN_INDUSTRY"));
        rules.put("服务区域", new Rule("ServiceRegion", "SERVES_REGION"));
        rules.put("工作单位", new Rule("Organization", "BELONGS_TO"));
        rules.put("职称", new Rule("Credential", "HAS_CREDENTIAL"));
        return Map.copyOf(rules);
    }

    private record Rule(String entityType, String relationType)
    {
    }
}
