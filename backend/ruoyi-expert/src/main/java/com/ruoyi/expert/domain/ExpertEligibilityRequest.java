package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 业务侧专家资格核验请求。
 */
public class ExpertEligibilityRequest
{
    @NotNull(message = "专家用户ID不能为空")
    private Long userId;

    @Size(max = 128, message = "外部任务号不能超过128个字符")
    private String externalTaskId;

    @Size(max = 128, message = "任务类型不能超过128个字符")
    private String taskType;

    @NotBlank(message = "服务开始时间不能为空")
    @Size(max = 32, message = "服务开始时间格式不正确")
    private String serviceStart;

    @NotBlank(message = "服务结束时间不能为空")
    @Size(max = 32, message = "服务结束时间格式不正确")
    private String serviceEnd;

    @Size(max = 16, message = "服务方式不能超过16个字符")
    private String serviceMode;

    @Size(max = 128, message = "服务区域不能超过128个字符")
    private String serviceRegion;

    @Size(max = 128, message = "产业方向不能超过128个字符")
    private String industry;

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getExternalTaskId()
    {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId)
    {
        this.externalTaskId = trim(externalTaskId);
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskType)
    {
        this.taskType = trim(taskType);
    }

    public String getServiceStart()
    {
        return serviceStart;
    }

    public void setServiceStart(String serviceStart)
    {
        this.serviceStart = trim(serviceStart);
    }

    public String getServiceEnd()
    {
        return serviceEnd;
    }

    public void setServiceEnd(String serviceEnd)
    {
        this.serviceEnd = trim(serviceEnd);
    }

    public String getServiceMode()
    {
        return serviceMode;
    }

    public void setServiceMode(String serviceMode)
    {
        this.serviceMode = trim(serviceMode);
    }

    public String getServiceRegion()
    {
        return serviceRegion;
    }

    public void setServiceRegion(String serviceRegion)
    {
        this.serviceRegion = trim(serviceRegion);
    }

    public String getIndustry()
    {
        return industry;
    }

    public void setIndustry(String industry)
    {
        this.industry = trim(industry);
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
