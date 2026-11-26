package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 专家针对具体任务的利益冲突确认。
 */
public class ExpertTaskConflictConfirmation
{
    private Long confirmationId;

    @NotBlank(message = "外部任务号不能为空")
    @Size(max = 128, message = "外部任务号不能超过128个字符")
    private String externalTaskId;

    @NotNull(message = "专家用户ID不能为空")
    private Long userId;

    @NotBlank(message = "服务开始时间不能为空")
    private String serviceStart;

    @NotBlank(message = "服务结束时间不能为空")
    private String serviceEnd;

    @Size(max = 16, message = "服务方式不能超过16个字符")
    private String serviceMode;

    @Size(max = 128, message = "服务区域不能超过128个字符")
    private String serviceRegion;

    @Size(max = 128, message = "产业方向不能超过128个字符")
    private String industry;

    private String conflictStatus;

    private String status;

    private String arrangementStatus;
    private String arrangedBy;
    private String arrangedAt;
    private String arrangementSource;
    private String arrangementVersion;

    @Size(max = 2000, message = "冲突说明不能超过2000个字符")
    private String statement;

    private String requestedBy;

    private String requestTime;

    private String confirmedAt;

    private String sourceSystem;

    private Integer version;

    public Long getConfirmationId()
    {
        return confirmationId;
    }

    public void setConfirmationId(Long confirmationId)
    {
        this.confirmationId = confirmationId;
    }

    public String getExternalTaskId()
    {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId)
    {
        this.externalTaskId = trim(externalTaskId);
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getServiceStart()
    {
        return serviceStart;
    }

    public void setServiceStart(String serviceStart)
    {
        this.serviceStart = serviceStart;
    }

    public String getServiceEnd()
    {
        return serviceEnd;
    }

    public void setServiceEnd(String serviceEnd)
    {
        this.serviceEnd = serviceEnd;
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

    public String getConflictStatus()
    {
        return conflictStatus;
    }

    public void setConflictStatus(String conflictStatus)
    {
        this.conflictStatus = conflictStatus;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getArrangementStatus()
    {
        return arrangementStatus;
    }

    public void setArrangementStatus(String arrangementStatus)
    {
        this.arrangementStatus = arrangementStatus;
    }

    public String getArrangedBy()
    {
        return arrangedBy;
    }

    public void setArrangedBy(String arrangedBy)
    {
        this.arrangedBy = arrangedBy;
    }

    public String getArrangedAt()
    {
        return arrangedAt;
    }

    public void setArrangedAt(String arrangedAt)
    {
        this.arrangedAt = arrangedAt;
    }

    public String getArrangementSource()
    {
        return arrangementSource;
    }

    public void setArrangementSource(String arrangementSource)
    {
        this.arrangementSource = arrangementSource;
    }

    public String getArrangementVersion()
    {
        return arrangementVersion;
    }

    public void setArrangementVersion(String arrangementVersion)
    {
        this.arrangementVersion = arrangementVersion;
    }

    public String getStatement()
    {
        return statement;
    }

    public void setStatement(String statement)
    {
        this.statement = statement;
    }

    public String getRequestedBy()
    {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy)
    {
        this.requestedBy = requestedBy;
    }

    public String getRequestTime()
    {
        return requestTime;
    }

    public void setRequestTime(String requestTime)
    {
        this.requestTime = requestTime;
    }

    public String getConfirmedAt()
    {
        return confirmedAt;
    }

    public void setConfirmedAt(String confirmedAt)
    {
        this.confirmedAt = confirmedAt;
    }

    public String getSourceSystem()
    {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem)
    {
        this.sourceSystem = sourceSystem;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
