package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 专家接单状态变更参数。
 */
public class ExpertStatusChange
{
    @NotBlank(message = "目标状态不能为空")
    private String serviceStatus;

    @NotBlank(message = "状态变更原因不能为空")
    private String reason;

    private boolean creditRelated;

    public String getServiceStatus()
    {
        return serviceStatus;
    }

    public void setServiceStatus(String serviceStatus)
    {
        this.serviceStatus = serviceStatus;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public boolean isCreditRelated()
    {
        return creditRelated;
    }

    public void setCreditRelated(boolean creditRelated)
    {
        this.creditRelated = creditRelated;
    }
}
