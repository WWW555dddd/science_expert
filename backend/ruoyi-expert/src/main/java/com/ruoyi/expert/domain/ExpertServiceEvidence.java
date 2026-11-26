package com.ruoyi.expert.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 外部服务证据接收记录。
 */
public class ExpertServiceEvidence extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long evidenceId;
    private String sourceEventId;
    private Long userId;
    private String evidenceType;
    private String externalTaskId;
    private String sourceSystem;
    private String sourceKind;
    private String occurredAt;
    private String title;
    private String summary;
    private String rawPayload;
    private String sourceDigest;
    private String processingStatus;
    private String receivedBy;

    public Long getEvidenceId()
    {
        return evidenceId;
    }

    public void setEvidenceId(Long evidenceId)
    {
        this.evidenceId = evidenceId;
    }

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

    public String getEvidenceType()
    {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType)
    {
        this.evidenceType = evidenceType;
    }

    public String getExternalTaskId()
    {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId)
    {
        this.externalTaskId = externalTaskId;
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
        this.sourceDigest = sourceDigest;
    }

    public String getProcessingStatus()
    {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus)
    {
        this.processingStatus = processingStatus;
    }

    public String getReceivedBy()
    {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy)
    {
        this.receivedBy = receivedBy;
    }
}
