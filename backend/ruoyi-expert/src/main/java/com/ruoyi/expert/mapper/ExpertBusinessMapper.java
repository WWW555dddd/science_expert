package com.ruoyi.expert.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * 业务经办人员只读查询。
 */
public interface ExpertBusinessMapper
{
    List<Map<String, Object>> selectBusinessExpertList(Map<String, Object> query);

    Map<String, Object> selectLatestConflictDisclosure(@Param("userId") Long userId);

    List<Map<String, Object>> selectOccupancySnapshots(
            @Param("userId") Long userId,
            @Param("serviceStart") String serviceStart,
            @Param("serviceEnd") String serviceEnd);
}
