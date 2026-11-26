package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 外部投诉、低评价或信用预警事件。
 */
public class ExpertRiskEventRequest
{
    @NotBlank(message = "来源事件ID不能为空")
    @Size(max = 128, message = "来源事件ID不能超过128个字符")
    private String sourceEventId;

    @NotNull(message = "专家用户ID不能为空")
    private Long userId;

    @NotBlank(message = "风险类型不能为空")
    private String riskType;

    @NotBlank(message = "风险等级不能为空")
    private String severity;

    @NotBlank(message = "风险标题不能为空")
    @Size(max = 255, message = "风险标题不能超过255个字符")
    private String title;

    @Size(max = 2000, message = "风险摘要不能超过2000个字符")
    private String summary;

    @NotBlank(message = "来源系统不能为空")
    @Size(max = 128, message = "来源系统不能超过128个字符")
    private String sourceSystem;

    @Size(max = 32, message = "来源类型不能超过32个字符")
    private String sourceKind;

    @Size(max = 32, message = "发生时间格式不正确")
    private String occurredAt;

    public String getSourceEventId()
    {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId)
    {
        this.sourceEventId = sourceEventId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getRiskType()
    {
        return riskType;
    }

    public void setRiskType(String riskType)
    {
        this.riskType = riskType;
    }

    public String getSeverity()
    {
        return severity;
    }

    public void setSeverity(String severity)
    {
        this.severity = severity;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getSourceSystem()
    {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem)
    {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceKind()
    {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind)
    {
        this.sourceKind = sourceKind;
    }

    public String getOccurredAt()
    {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt)
    {
        this.occurredAt = occurredAt;
    }
}
