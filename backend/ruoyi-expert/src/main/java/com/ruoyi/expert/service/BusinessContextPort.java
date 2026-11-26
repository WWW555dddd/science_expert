package com.ruoyi.expert.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 外部业务上下文查询端口。
 *
 * 当前由模拟数据适配器实现，未来接入派单系统时替换实现即可。
 */
public interface BusinessContextPort
{
    BusinessContextResult inspect(BusinessContextQuery query);

    record BusinessContextQuery(
            Long userId,
            String externalTaskId,
            LocalDateTime serviceStart,
            LocalDateTime serviceEnd,
            String serviceMode,
            String serviceRegion,
            String industry)
    {
    }

    record BusinessContextResult(
            String occupancyStatus,
            String conflictStatus,
            String source,
            String verifiedAt,
            List<String> taskRefs)
    {
    }
}
