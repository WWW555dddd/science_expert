package com.ruoyi.expert.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 专家可服务时间。
 */
public class ExpertSchedule extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long scheduleId;
    private Long userId;
    private Integer weekday;
    private String startTime;
    private String endTime;
    private String serviceMode;
    private Integer weeklyCapacity;
    private String effectiveFrom;

    public Long getScheduleId()
    {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId)
    {
        this.scheduleId = scheduleId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    @Min(value = 1, message = "星期必须在1到7之间")
    @Max(value = 7, message = "星期必须在1到7之间")
    public Integer getWeekday()
    {
        return weekday;
    }

    public void setWeekday(Integer weekday)
    {
        this.weekday = weekday;
    }

    @NotBlank(message = "开始时间不能为空")
    public String getStartTime()
    {
        return startTime;
    }

    public void setStartTime(String startTime)
    {
        this.startTime = startTime;
    }

    @NotBlank(message = "结束时间不能为空")
    public String getEndTime()
    {
        return endTime;
    }

    public void setEndTime(String endTime)
    {
        this.endTime = endTime;
    }

    public String getServiceMode()
    {
        return serviceMode;
    }

    public void setServiceMode(String serviceMode)
    {
        this.serviceMode = serviceMode;
    }

    public Integer getWeeklyCapacity()
    {
        return weeklyCapacity;
    }

    public void setWeeklyCapacity(Integer weeklyCapacity)
    {
        this.weeklyCapacity = weeklyCapacity;
    }

    public String getEffectiveFrom()
    {
        return effectiveFrom;
    }

    public void setEffectiveFrom(String effectiveFrom)
    {
        this.effectiveFrom = effectiveFrom;
    }
}
