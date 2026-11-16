package com.ruoyi.expert.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertQualityEvaluationMapper;

/**
 * 从当前数据库结果计算抽取质量和画像一致性指标。
 */
@Service
public class ExpertQualityEvaluationService
{
    @Autowired
    private ExpertQualityEvaluationMapper evalMapper;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private KnowledgeGraphQueryService graphQueryService;

    @Autowired
    private ExpertDimensionViewService dimensionViewService;

    public List<Map<String, Object>> datasets()
    {
        return evalMapper.selectDatasets();
    }

    public Map<String, Object> run(Long datasetId, String createBy)
    {
        List<Map<String, Object>> cases = evalMapper.selectCases(datasetId);
        if (cases.isEmpty())
        {
            throw new ServiceException("评估数据集没有案例");
        }
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("datasetId", datasetId);
        run.put("caseCount", cases.size());
        run.put("createBy", createBy);
        evalMapper.insertRun(run);
        Long runId = ((Number) run.get("runId")).longValue();

        List<Map<String, Object>> caseResults = new ArrayList<>();
        try
        {
            for (Map<String, Object> evalCase : cases)
            {
                Map<String, Object> result = evaluateCase(evalCase);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("runId", runId);
                row.put("caseId", evalCase.get("caseId"));
                row.put("userId", evalCase.get("userId"));
                row.put("metricsJson", JSON.toJSONString(result.get("metrics")));
                row.put("detailJson", JSON.toJSONString(result));
                evalMapper.insertCaseResult(row);
                caseResults.add(result);
            }
            Map<String, Object> summary = summarize(caseResults);
            JSONObject report = new JSONObject();
            report.put("datasetId", datasetId);
            report.put("summary", summary);
            report.put("cases", caseResults);
            run.put("runId", runId);
            run.put("runStatus", "COMPLETED");
            run.put("caseCount", cases.size());
            run.put("metricsJson", JSON.toJSONString(summary));
            run.put("reportJson", report.toJSONString());
            run.put("errorMessage", null);
            evalMapper.updateRun(run);
            Map<String, Object> output = new LinkedHashMap<>(run);
            output.put("summary", summary);
            return output;
        }
        catch (Exception ex)
        {
            run.put("runId", runId);
            run.put("runStatus", "FAILED");
            run.put("caseCount", cases.size());
            run.put("metricsJson", null);
            run.put("reportJson", null);
            run.put("errorMessage", ex.getMessage());
            evalMapper.updateRun(run);
            throw new ServiceException("质量评估失败：" + ex.getMessage());
        }
    }

    public Map<String, Object> getRun(Long runId)
    {
        Map<String, Object> run = evalMapper.selectRun(runId);
        if (run == null)
        {
            throw new ServiceException("评估运行不存在");
        }
        run.put("results", evalMapper.selectRunResults(runId));
        return run;
    }

    private Map<String, Object> evaluateCase(Map<String, Object> evalCase)
    {
        Long userId = ((Number) evalCase.get("userId")).longValue();
        Set<String> actualFields = actualFields(userId);
        Set<String> goldFields = jsonSet(evalCase.get("goldFieldsJson"), "field");

        Map<String, Object> graph = graphQueryService.expertSubgraph(userId, 3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actualRelations =
                (List<Map<String, Object>>) graph.getOrDefault("relations", List.of());
        Set<String> actualEntities = entitySet(
                (List<Map<String, Object>>) graph.getOrDefault("entities", List.of()));
        actualEntities.addAll(entitySet(graphQueryService.extractedEntitiesForExpert(userId)));
        Set<String> goldEntities = jsonSet(evalCase.get("goldEntitiesJson"), "entity");
        Set<String> actualRelationKeys = relationSet(actualRelations);
        Set<String> goldRelations = jsonSet(evalCase.get("goldRelationsJson"), "relation");

        List<ExpertGraphDimension> dimensions =
                dimensionViewService.dimensions(userId, "DRAFT");
        Set<String> actualConflicts = new LinkedHashSet<>();
        int knownDimensions = 0;
        int evidenceDimensions = 0;
        int comparableDimensions = 0;
        List<Double> ranges = new ArrayList<>();
        for (ExpertGraphDimension dimension : dimensions)
        {
            if (!"INSUFFICIENT_EVIDENCE".equals(dimension.getDataStatus()))
            {
                knownDimensions++;
            }
            if (dimension.getEvidenceItems() != null && !dimension.getEvidenceItems().isEmpty())
            {
                evidenceDimensions++;
                List<Double> scores = dimension.getEvidenceItems().stream()
                        .map(item -> numberOrNull(item.get("score")))
                        .filter(Objects::nonNull)
                        .toList();
                if (scores.size() >= 2)
                {
                    comparableDimensions++;
                    ranges.add(scores.stream().mapToDouble(Double::doubleValue).max().orElse(0)
                            - scores.stream().mapToDouble(Double::doubleValue).min().orElse(0));
                }
            }
            if ("CONFLICT".equals(dimension.getDataStatus()))
            {
                actualConflicts.add(dimension.getDimensionCode());
            }
        }
        Set<String> goldConflicts = stringSet(evalCase.get("goldConflictsJson"));
        Map<String, Object> fieldMetric = setMetric(actualFields, goldFields);
        Map<String, Object> entityMetric = setMetric(actualEntities, goldEntities);
        Map<String, Object> relationMetric = setMetric(actualRelationKeys, goldRelations);
        Map<String, Object> conflictMetric = setMetric(actualConflicts, goldConflicts);

        double averageRange = ranges.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        Double crossMaterialConsistency = ranges.isEmpty()
                ? null : round(Math.max(0, 100 - averageRange));
        double completeness = dimensions.isEmpty() ? 0
                : knownDimensions * 100.0 / dimensions.size();
        double evidenceCoverage = dimensions.isEmpty() ? 0
                : evidenceDimensions * 100.0 / dimensions.size();
        Double portraitConsistency = crossMaterialConsistency == null
                ? null : round((completeness + crossMaterialConsistency + evidenceCoverage) / 3);

        int relationsWithEvidence = 0;
        int materialEvidenceCount = 0;
        int locatedMaterialEvidenceCount = 0;
        for (Map<String, Object> relation : actualRelations)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> evidenceItems =
                    (List<Map<String, Object>>) relation.getOrDefault("evidence", List.of());
            if (!evidenceItems.isEmpty())
            {
                relationsWithEvidence++;
            }
            for (Map<String, Object> evidence : evidenceItems)
            {
                if (evidence.get("materialId") == null)
                {
                    continue;
                }
                materialEvidenceCount++;
                if (evidence.get("pageNo") != null || evidence.get("charStart") != null)
                {
                    locatedMaterialEvidenceCount++;
                }
            }
        }
        double relationEvidenceCoverage = actualRelations.isEmpty() ? 0
                : relationsWithEvidence * 100.0 / actualRelations.size();
        double evidenceLocationCoverage = materialEvidenceCount == 0 ? 0
                : locatedMaterialEvidenceCount * 100.0 / materialEvidenceCount;

        double fieldF1 = number(fieldMetric.get("f1"));
        double entityF1 = number(entityMetric.get("f1"));
        double relationF1 = number(relationMetric.get("f1"));
        double weightedQuality = fieldF1 * .25
                + entityF1 * .15
                + relationF1 * .15
                + evidenceCoverage * .10
                + completeness * .10
                + relationEvidenceCoverage * .10
                + evidenceLocationCoverage * .10;
        double qualityWeight = 1.0;
        if (crossMaterialConsistency != null)
        {
            weightedQuality += crossMaterialConsistency * .15;
            qualityWeight += .15;
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("fieldPrecision", fieldMetric.get("precision"));
        metrics.put("fieldRecall", fieldMetric.get("recall"));
        metrics.put("fieldF1", fieldMetric.get("f1"));
        metrics.put("entityPrecision", entityMetric.get("precision"));
        metrics.put("entityRecall", entityMetric.get("recall"));
        metrics.put("entityF1", entityMetric.get("f1"));
        metrics.put("relationPrecision", relationMetric.get("precision"));
        metrics.put("relationRecall", relationMetric.get("recall"));
        metrics.put("relationF1", relationMetric.get("f1"));
        metrics.put("conflictF1", conflictMetric.get("f1"));
        metrics.put("portraitCompleteness", round(completeness));
        metrics.put("portraitEvidenceCoverage", round(evidenceCoverage));
        metrics.put("crossMaterialConsistency", crossMaterialConsistency);
        metrics.put("crossMaterialComparableDimensions", comparableDimensions);
        metrics.put("portraitConsistency", portraitConsistency);
        metrics.put("relationEvidenceCoverage", round(relationEvidenceCoverage));
        metrics.put("evidenceLocationCoverage", round(evidenceLocationCoverage));
        metrics.put("overallQuality", round(weightedQuality / qualityWeight));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("caseId", evalCase.get("caseId"));
        detail.put("caseName", evalCase.get("caseName"));
        detail.put("userId", userId);
        detail.put("metrics", metrics);
        detail.put("missingFields", minus(goldFields, actualFields));
        detail.put("extraFields", minus(actualFields, goldFields));
        detail.put("missingEntities", minus(goldEntities, actualEntities));
        detail.put("extraEntities", minus(actualEntities, goldEntities));
        detail.put("missingRelations", minus(goldRelations, actualRelationKeys));
        detail.put("extraRelations", minus(actualRelationKeys, goldRelations));
        detail.put("actualConflicts", actualConflicts);
        detail.put("goldConflicts", goldConflicts);
        detail.put("materialEvidenceCount", materialEvidenceCount);
        detail.put("locatedMaterialEvidenceCount", locatedMaterialEvidenceCount);
        return detail;
    }

    private Set<String> actualFields(Long userId)
    {
        ExpertMaterial query = new ExpertMaterial();
        query.setUserId(userId);
        List<ExpertMaterial> materials = platformMapper.selectMaterialList(query);
        Map<String, ExpertMaterial> latestByCategory = new LinkedHashMap<>();
        for (ExpertMaterial material : materials)
        {
            latestByCategory.putIfAbsent(material.getMaterialCategory(), material);
        }
        Set<String> result = new LinkedHashSet<>();
        for (ExpertMaterial material : latestByCategory.values())
        {
            for (Map<String, Object> mapping :
                    platformMapper.selectFieldMappings(material.getMaterialId()))
            {
                String field = String.valueOf(mapping.get("dbField"));
                String value = normalize(String.valueOf(mapping.get("extractedValue")));
                if (!value.isBlank())
                {
                    result.add(field + "|" + value);
                }
            }
        }
        return result;
    }

    private static Set<String> entitySet(List<Map<String, Object>> entities)
    {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> entity : entities)
        {
            result.add(entityKey(String.valueOf(entity.get("entityType")),
                    String.valueOf(entity.get("canonicalName"))));
        }
        return result;
    }

    private static Set<String> relationSet(List<Map<String, Object>> relations)
    {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> relation : relations)
        {
            result.add(String.join("|",
                    String.valueOf(relation.get("sourceEntityType")),
                    normalize(String.valueOf(relation.get("sourceEntityName"))),
                    String.valueOf(relation.get("relationType")),
                    String.valueOf(relation.get("targetEntityType")),
                    normalize(String.valueOf(relation.get("targetEntityName")))));
        }
        return result;
    }

    private static Set<String> jsonSet(Object value, String kind)
    {
        JSONArray array = parseArray(value);
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++)
        {
            JSONObject item = array.getJSONObject(index);
            if (item == null)
            {
                continue;
            }
            if ("field".equals(kind))
            {
                result.add(item.getString("dbField") + "|" + normalize(item.getString("value")));
            }
            else if ("entity".equals(kind))
            {
                result.add(entityKey(item.getString("entityType"), item.getString("name")));
            }
            else
            {
                result.add(String.join("|",
                        item.getString("sourceType"),
                        normalize(item.getString("sourceName")),
                        item.getString("relationType"),
                        item.getString("targetType"),
                        normalize(item.getString("targetName"))));
            }
        }
        return result;
    }

    private static Set<String> stringSet(Object value)
    {
        JSONArray array = parseArray(value);
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < array.size(); index++)
        {
            String item = array.getString(index);
            if (item != null && !item.isBlank())
            {
                result.add(item);
            }
        }
        return result;
    }

    private static JSONArray parseArray(Object value)
    {
        if (value == null)
        {
            return new JSONArray();
        }
        if (value instanceof JSONArray array)
        {
            return array;
        }
        try
        {
            return JSON.parseArray(value.toString());
        }
        catch (Exception ex)
        {
            return new JSONArray();
        }
    }

    private static String entityKey(String type, String name)
    {
        return type + "|" + normalize(name);
    }

    private static Map<String, Object> setMetric(Set<String> actual, Set<String> gold)
    {
        Set<String> tp = new LinkedHashSet<>(actual);
        tp.retainAll(gold);
        double precision = actual.isEmpty()
                ? (gold.isEmpty() ? 100 : 0) : tp.size() * 100.0 / actual.size();
        double recall = gold.isEmpty()
                ? (actual.isEmpty() ? 100 : 0) : tp.size() * 100.0 / gold.size();
        double f1 = precision + recall == 0 ? 0
                : 2 * precision * recall / (precision + recall);
        Map<String, Object> result = new HashMap<>();
        result.put("precision", round(precision));
        result.put("recall", round(recall));
        result.put("f1", round(f1));
        return result;
    }

    private static Map<String, Object> summarize(List<Map<String, Object>> results)
    {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> result : results)
        {
            @SuppressWarnings("unchecked")
            Map<String, Object> metrics = (Map<String, Object>) result.get("metrics");
            for (Map.Entry<String, Object> entry : metrics.entrySet())
            {
                Double value = numberOrNull(entry.getValue());
                if (value != null)
                {
                    totals.merge(entry.getKey(), value, Double::sum);
                    counts.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        totals.forEach((name, total) ->
                summary.put(name, round(total / counts.get(name))));
        return summary;
    }

    private static Set<String> minus(Set<String> left, Set<String> right)
    {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static String normalize(String value)
    {
        if (value == null || "null".equals(value))
        {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）()【】\\[\\]]+", "");
    }

    private static double number(Object value)
    {
        Double result = numberOrNull(value);
        return result == null ? 0 : result;
    }

    private static Double numberOrNull(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.doubleValue();
        }
        try
        {
            return Double.parseDouble(String.valueOf(value));
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }

    private static double round(double value)
    {
        return Math.round(value * 100.0) / 100.0;
    }
}
