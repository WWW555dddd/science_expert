package com.ruoyi.expert.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmation;

/**
 * 任务级利益冲突确认。
 */
public interface ExpertTaskConflictMapper
{
    int insertConfirmation(ExpertTaskConflictConfirmation confirmation);

    ExpertTaskConflictConfirmation selectById(Long confirmationId);

    ExpertTaskConflictConfirmation selectByIdForUpdate(Long confirmationId);

    ExpertTaskConflictConfirmation selectByTaskAndExpert(
            @Param("externalTaskId") String externalTaskId,
            @Param("userId") Long userId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd);

    ExpertTaskConflictConfirmation selectLatestConfirmed(
            @Param("externalTaskId") String externalTaskId,
            @Param("userId") Long userId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd);

    ExpertTaskConflictConfirmation selectLatestByTask(
            @Param("externalTaskId") String externalTaskId,
            @Param("userId") Long userId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd);

    List<ExpertTaskConflictConfirmation> selectByExpert(
            @Param("userId") Long userId, @Param("status") String status);

    int confirm(@Param("confirmationId") Long confirmationId,
            @Param("userId") Long userId,
            @Param("acceptanceStatus") String acceptanceStatus,
            @Param("conflictStatus") String conflictStatus,
            @Param("statement") String statement,
            @Param("version") Integer version);

    int updateArrangement(@Param("confirmationId") Long confirmationId,
            @Param("expectedStatus") String expectedStatus,
            @Param("targetStatus") String targetStatus,
            @Param("arrangedBy") String arrangedBy,
            @Param("arrangementSource") String arrangementSource,
            @Param("arrangementVersion") String arrangementVersion,
            @Param("version") Integer version);

    int upsertOccupancySnapshot(@Param("userId") Long userId,
            @Param("externalTaskId") String externalTaskId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd,
            @Param("serviceMode") String serviceMode,
            @Param("serviceRegion") String serviceRegion,
            @Param("industry") String industry,
            @Param("occupancyStatus") String occupancyStatus,
            @Param("conflictStatus") String conflictStatus,
            @Param("sourceSystem") String sourceSystem,
            @Param("sourceKind") String sourceKind,
            @Param("sourceVersion") String sourceVersion,
            @Param("operator") String operator);
}
