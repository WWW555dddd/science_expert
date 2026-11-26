package com.ruoyi.expert.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.expert.domain.ExpertBusinessContextSnapshot;
import com.ruoyi.expert.domain.ExpertServiceEvidence;

/**
 * 专家开放接口的数据访问。
 */
public interface ExpertOpenApiMapper
{
    List<ExpertBusinessContextSnapshot> selectOverlappingBusinessContexts(
            @Param("userId") Long userId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd);

    ExpertServiceEvidence selectServiceEvidenceBySourceEventId(
            @Param("sourceEventId") String sourceEventId);

    List<ExpertServiceEvidence> selectRecentServiceEvidenceByUserId(
            @Param("userId") Long userId, @Param("limit") Integer limit);

    int insertServiceEvidence(ExpertServiceEvidence evidence);

    int updateServiceEvidenceStatus(@Param("evidenceId") Long evidenceId,
            @Param("processingStatus") String processingStatus);
}
