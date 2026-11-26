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
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 多材料30维画像聚合。
 */
@Service
public class ExpertProfileDimensionAggregationService
{
    private static final double CONFLICT_SCORE_GAP = 25.0;
    private static final String PROFILE_KIND = "EVIDENCE_SCORE";
    private static final String PROFILE_RULE_VERSION = "profile-assessment-v3";

    @Autowired
    private ExpertPlatformMapper platformMapper;

    /**
     * 保存独立的材料级画像评估，再重建30维草稿画像。
     */
    @Transactional
    public void recordAndAggregate(ExpertProfile profile, Long materialId,
            JSONObject analysis, boolean accepted)
    {
        platformMapper.deleteDimensionAssessmentsByMaterial(materialId);
        JSONObject scores = analysis == null ? null : analysis.getJSONObject("scores");
        if (scores != null && !scores.isEmpty())
        {
            Map<String, ExpertGraphDimension> definitions = new LinkedHashMap<>();
            for (ExpertGraphDimension dimension :
                    platformMapper.selectGraphDimensions(profile.getUserId(), "DRAFT"))
            {
                definitions.put(dimension.getDimensionCode(), dimension);
            }
            for (Map.Entry<String, Object> entry : scores.entrySet())
            {
                String dimensionCode = entry.getKey();
                if (!definitions.containsKey(dimensionCode)
                        || !(entry.getValue() instanceof JSONObject score))
                {
                    continue;
                }
                Object scoreValue = score.get("score");
                Object confidenceValue = score.get("confidence");
                String evidence = score.getString("evidence");
                if (!(scoreValue instanceof Number) || !(confidenceValue instanceof Number)
                        || evidence == null || evidence.isBlank())
                {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userId", profile.getUserId());
                row.put("materialId", materialId);
                row.put("dimensionCode", dimensionCode);
                row.put("score", clamp(((Number) scoreValue).doubleValue(), 0, 100));
                row.put("confidence", clamp(((Number) confidenceValue).doubleValue(), 0, 1));
                row.put("evidence", abbreviate(evidence, 1000));
                row.put("reviewStatus", accepted ? "ACCEPTED" : "PENDING");
                row.put("ruleVersion", PROFILE_RULE_VERSION);
                platformMapper.upsertDimensionAssessment(row);
            }
        }
        rebuildDraft(profile, false);
    }

    public void rebuildDraft(ExpertProfile profile)
    {
        rebuildDraft(profile, false);
    }

    /**
     * @param acceptedOnly 正式发布前只接受已审核的画像材料评估。
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
                platformMapper.selectDimensionAssessments(profile.getUserId(), acceptedOnly))
        {
            String dimensionCode = stringValue(row.get("dimensionCode"));
            if (dimensionCode == null || dimensionCode.isBlank())
            {
                continue;
            }
            String evidence = stringValue(row.get("evidence"));
            if (!ExpertAgentAnalysisValidator.hasUsableEvidence(evidence))
            {
                continue;
            }
            GraphAssessment assessment = new GraphAssessment(
                    dimensionCode,
                    longValue(row.get("materialId")),
                    longValue(row.get("assessmentId")),
                    doubleValue(row.get("score")),
                    doubleValue(row.get("confidence")),
                    evidence,
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
                evidenceTexts.add("材料#" + item.materialId() + "：" + item.evidence());

                Map<String, Object> evidenceRow = new LinkedHashMap<>();
                evidenceRow.put("userId", profile.getUserId());
                evidenceRow.put("dimensionCode", definition.getDimensionCode());
                evidenceRow.put("materialId", item.materialId());
                evidenceRow.put("graphStatus", "DRAFT");
                evidenceRow.put("score", score);
                evidenceRow.put("confidence", itemConfidence);
                evidenceRow.put("evidence", abbreviate(item.evidence(), 1000));
                evidenceRow.put("inferenceMethod", "DIMENSION_ASSESSMENT");
                evidenceRow.put("graphRelationId", null);
                evidenceRow.put("graphEntityId", null);
                evidenceRow.put("graphEvidenceId", null);
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
                    "DIMENSION_ASSESSMENT_AGGREGATE",
                    materialIds.size(),
                    null,
                    null,
                    null,
                    0,
                    PROFILE_RULE_VERSION);
        }
    }

    private static List<GraphAssessment> uniqueAssessments(List<GraphAssessment> items)
    {
        Map<Long, GraphAssessment> unique = new LinkedHashMap<>();
        for (GraphAssessment item : items)
        {
            unique.putIfAbsent(item.assessmentId(), item);
        }
        return new ArrayList<>(unique.values());
    }

    private static double doubleValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.doubleValue();
        }
        try
        {
            return Double.parseDouble(String.valueOf(value));
        }
        catch (Exception ex)
        {
            return 0;
        }
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
            Long assessmentId,
            double score, double confidence, String evidence, String fileName)
    {
    }
}
