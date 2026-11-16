package com.ruoyi.expert.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 专家抽取质量与画像一致性评估。
 */
public interface ExpertQualityEvaluationMapper
{
    List<Map<String, Object>> selectDatasets();

    List<Map<String, Object>> selectCases(@Param("datasetId") Long datasetId);

    int insertRun(Map<String, Object> run);

    int updateRun(Map<String, Object> run);

    int insertCaseResult(Map<String, Object> result);

    Map<String, Object> selectRun(@Param("runId") Long runId);

    List<Map<String, Object>> selectRunResults(@Param("runId") Long runId);
}
