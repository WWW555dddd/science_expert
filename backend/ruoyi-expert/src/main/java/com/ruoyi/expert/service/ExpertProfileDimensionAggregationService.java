package com.ruoyi.expert.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 多材料30维画像聚合。
 */
@Service
public class ExpertProfileDimensionAggregationService
{
    private static final double CONFLICT_SCORE_GAP = 25.0;
    private static final String PROFILE_KIND = "GRAPH_PROFILE";
    private static final String PROFILE_RULE_VERSION = "graph-profile-v1";

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    /**
     * 兼容旧调用。模型分数已经在此之前写入 CapabilityAssessment 图谱节点，
     * 这里只从事先落库的图谱声明重建画像。
     */
    @Transactional
    public void recordAndAggregate(ExpertProfile profile, Long materialId,
            JSONObject analysis)
    {
        rebuildDraft(profile, false);
    }

    public void rebuildDraft(ExpertProfile profile)
    {
        rebuildDraft(profile, false);
    }

    /**
     * @param acceptedOnly 正式发布前只接受已审核且有效的图谱声明。
     */
    @Transactional
    public void rebuildDraft(ExpertProfile profile, boolean acceptedOnly)
    {
        platformMapper.deleteDraftGraph(profile.getUserId());
        platformMapper.deleteDraftDimensionEvidence(profile.getUserId());

        List<ExpertGraphDimension> definitions =
                platformMapper.selectGraphDimensions(profile.getUserId(), "DRAFT");
        Map<String, List<GraphAssessment>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row :
                graphMapper.selectDimensionAssessments(profile.getUserId(), acceptedOnly))
        {
            JSONObject properties = parseProperties(row.get("propertiesJson"));
            String dimensionCode = properties.getString("dimensionCode");
            if (dimensionCode == null || dimensionCode.isBlank())
            {
                continue;
            }
            GraphAssessment assessment = new GraphAssessment(
                    dimensionCode,
                    longValue(row.get("materialId")),
                    longValue(row.get("assessmentEntityId")),
                    longValue(row.get("relationId")),
                    longValue(row.get("evidenceId")),
                    properties.getDoubleValue("score"),
                    properties.getDoubleValue("confidence"),
                    stringValue(row.get("evidence")),
                    stringValue(row.get("fileName")));
            grouped.computeIfAbsent(dimensionCode, key -> new ArrayList<>()).add(assessment);
        }
        for (ExpertGraphDimension definition : definitions)
        {
            List<GraphAssessment> items = grouped.get(definition.getDimensionCode());
            if (items == null || items.isEmpty())
            {
                continue;
            }
            List<GraphAssessment> unique = uniqueAssessments(items);
            double weightedScore = 0;
            double weight = 0;
            double confidence = 0;
            double min = 100;
            double max = 0;
            List<String> evidenceTexts = new ArrayList<>();
            Set<Long> materialIds = new LinkedHashSet<>();
            Set<Long> assessmentIds = new LinkedHashSet<>();
            Set<Long> relationIds = new LinkedHashSet<>();
            Set<Long> evidenceIds = new LinkedHashSet<>();
            for (GraphAssessment item : unique)
            {
                double score = clamp(item.score(), 0, 100);
                double itemConfidence = clamp(item.confidence(), 0.05, 1);
                weightedScore += score * itemConfidence;
                weight += itemConfidence;
                confidence += itemConfidence;
                min = Math.min(min, score);
                max = Math.max(max, score);
                materialIds.add(item.materialId());
                assessmentIds.add(item.assessmentEntityId());
                relationIds.add(item.relationId());
                evidenceIds.add(item.evidenceId());
                evidenceTexts.add("材料#" + item.materialId() + "：" + item.evidence());

                Map<String, Object> evidenceRow = new LinkedHashMap<>();
                evidenceRow.put("userId", profile.getUserId());
                evidenceRow.put("dimensionCode", definition.getDimensionCode());
                evidenceRow.put("materialId", item.materialId());
                evidenceRow.put("graphStatus", "DRAFT");
                evidenceRow.put("score", score);
                evidenceRow.put("confidence", itemConfidence);
                evidenceRow.put("evidence", abbreviate(item.evidence(), 1000));
                evidenceRow.put("inferenceMethod", "GRAPH_ASSESSMENT");
                evidenceRow.put("graphRelationId", item.relationId());
                evidenceRow.put("graphEntityId", item.assessmentEntityId());
                evidenceRow.put("graphEvidenceId", item.evidenceId());
                evidenceRow.put("profileRuleVersion", PROFILE_RULE_VERSION);
                platformMapper.insertDimensionEvidence(evidenceRow);
            }
            boolean conflict = materialIds.size() >= 2 && max - min >= CONFLICT_SCORE_GAP;
            platformMapper.insertGraphScore(profile.getUserId(), definition.getDimensionCode(),
                    weight == 0 ? 0 : weightedScore / weight,
                    confidence / unique.size(),
                    abbreviate(String.join("；", evidenceTexts), 480),
                    "DRAFT",
                    profile.getGraphVersion() == null ? 0 : profile.getGraphVersion(),
                    PROFILE_KIND,
                    conflict ? "1" : "0",
                    "GRAPH_ASSESSMENT_AGGREGATE",
                    materialIds.size(),
                    joinIds(relationIds),
                    joinIds(assessmentIds),
                    joinIds(evidenceIds),
                    relationIds.size(),
                    PROFILE_RULE_VERSION);
        }
    }

    private static List<GraphAssessment> uniqueAssessments(List<GraphAssessment> items)
    {
        Map<Long, GraphAssessment> unique = new LinkedHashMap<>();
        for (GraphAssessment item : items)
        {
            unique.putIfAbsent(item.relationId(), item);
        }
        return new ArrayList<>(unique.values());
    }

    private static JSONObject parseProperties(Object value)
    {
        if (value instanceof JSONObject json)
        {
            return json;
        }
        if (value == null) {
            return new JSONObject();
        }
        try
        {
            return JSON.parseObject(String.valueOf(value));
        }
        catch (Exception ex)
        {
            return new JSONObject();
        }
    }

    private static String joinIds(Set<Long> ids)
    {
        return ids.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Long longValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (Exception ex)
        {
            return 0L;
        }
    }

    private static String stringValue(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static String abbreviate(String value, int maxLength)
    {
        if (value == null)
        {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private record GraphAssessment(String dimensionCode, Long materialId,
            Long assessmentEntityId, Long relationId, Long evidenceId,
            double score, double confidence, String evidence, String fileName)
    {
    }
}
