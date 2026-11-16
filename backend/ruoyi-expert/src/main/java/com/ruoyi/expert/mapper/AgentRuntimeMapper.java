package com.ruoyi.expert.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 运行与工具轨迹审计。
 */
public interface AgentRuntimeMapper
{
    int insertRun(Map<String, Object> run);

    int updateRun(@Param("runId") String runId,
            @Param("runStatus") String runStatus,
            @Param("stepCount") int stepCount,
            @Param("finalAnswer") String finalAnswer,
            @Param("errorMessage") String errorMessage);

    int insertStep(Map<String, Object> step);

    Map<String, Object> selectRun(@Param("runId") String runId);

    List<Map<String, Object>> selectSteps(@Param("runId") String runId);
}
