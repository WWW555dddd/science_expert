package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 外部服务报告、评价或信用事实接收请求。
 */
public class ExpertServiceEvidenceRequest
{
    @NotBlank(message = "来源事件ID不能为空")
    @Size(max = 128, message = "来源事件ID不能超过128个字符")
    private String sourceEventId;

    @NotNull(message = "专家用户ID不能为空")
    private Long userId;

    @NotBlank(message = "证据类型不能为空")
    private String evidenceType;

    @Size(max = 128, message = "外部任务号不能超过128个字符")
    private String externalTaskId;

    @NotBlank(message = "来源系统不能为空")
    @Size(max = 128, message = "来源系统不能超过128个字符")
    private String sourceSystem;

    @Size(max = 32, message = "来源类型不能超过32个字符")
    private String sourceKind;

    @Size(max = 32, message = "发生时间格式不正确")
    private String occurredAt;

    @Size(max = 255, message = "证据标题不能超过255个字符")
    private String title;

    @Size(max = 2000, message = "证据摘要不能超过2000个字符")
    private String summary;

    @Size(max = 20000, message = "原始载荷不能超过20000个字符")
    private String rawPayload;

    @Size(max = 128, message = "来源摘要不能超过128个字符")
    private String sourceDigest;

    public String getSourceEventId()
    {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId)
    {
        this.sourceEventId = trim(sourceEventId);
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getEvidenceType()
    {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType)
    {
        this.evidenceType = trim(evidenceType);
    }

    public String getExternalTaskId()
    {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId)
    {
        this.externalTaskId = trim(externalTaskId);
    }

    public String getSourceSystem()
    {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem)
    {
        this.sourceSystem = trim(sourceSystem);
    }

    public String getSourceKind()
    {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind)
    {
        this.sourceKind = trim(sourceKind);
    }

    public String getOccurredAt()
    {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt)
    {
        this.occurredAt = trim(occurredAt);
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = trim(title);
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = trim(summary);
    }

    public String getRawPayload()
    {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload)
    {
        this.rawPayload = rawPayload;
    }

    public String getSourceDigest()
    {
        return sourceDigest;
    }

    public void setSourceDigest(String sourceDigest)
    {
        this.sourceDigest = trim(sourceDigest);
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
