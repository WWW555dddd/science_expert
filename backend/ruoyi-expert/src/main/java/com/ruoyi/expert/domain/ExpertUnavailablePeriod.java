package com.ruoyi.expert.domain;

import java.util.Objects;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 专家临时不可用时段。
 */
public class ExpertUnavailablePeriod extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long unavailableId;
    private Long userId;
    private String userName;
    private String realName;
    private String startTime;
    private String endTime;
    private String reason;
    private Integer version;

    public Long getUnavailableId()
    {
        return unavailableId;
    }

    public void setUnavailableId(Long unavailableId)
    {
        this.unavailableId = unavailableId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = realName;
    }

    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason == null ? null : reason.trim();
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public boolean owns(Long expertUserId)
    {
        return Objects.equals(userId, expertUserId);
    }
}
