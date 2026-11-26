package com.ruoyi.expert.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.expert.domain.ExpertRiskReview;

/**
 * 投诉、低评价和信用预警审核。
 */
public interface ExpertRiskReviewMapper
{
    int insertRisk(ExpertRiskReview risk);

    ExpertRiskReview selectById(Long riskId);

    ExpertRiskReview selectByIdForUpdate(Long riskId);

    ExpertRiskReview selectBySource(@Param("sourceEventId") String sourceEventId,
            @Param("riskType") String riskType);

    List<ExpertRiskReview> selectList(@Param("status") String status);

    List<ExpertRiskReview> selectAcknowledgedByUserId(
            @Param("userId") Long userId, @Param("limit") Integer limit);

    int resolveRisk(@Param("riskId") Long riskId,
            @Param("status") String status,
            @Param("version") Integer version,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewerName") String reviewerName,
            @Param("comment") String comment);
}
