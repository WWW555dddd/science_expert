package com.ruoyi.expert.domain;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 批量读取专家匹配事实。
 */
public class ExpertSnapshotBatchRequest
{
    @NotEmpty(message = "专家用户ID列表不能为空")
    @Size(max = 100, message = "单次最多查询100位专家")
    private List<Long> userIds;

    public List<Long> getUserIds()
    {
        return userIds;
    }

    public void setUserIds(List<Long> userIds)
    {
        this.userIds = userIds;
    }
}
