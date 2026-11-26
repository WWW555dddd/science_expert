package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 投诉、低评价或信用预警的审核记录。
 */
public class ExpertRiskReview
{
    private Long riskId;
    private Long evidenceId;
    private String sourceEventId;
    private Long userId;
    private String realName;
    private Integer creditScore;
    @NotBlank(message = "风险类型不能为空")
    private String riskType;
    @NotBlank(message = "风险等级不能为空")
    private String severity;
    @NotBlank(message = "风险标题不能为空")
    @Size(max = 255, message = "风险标题不能超过255个字符")
    private String title;
    @Size(max = 2000, message = "风险摘要不能超过2000个字符")
    private String summary;
    private String status;
    private Long reviewerId;
    private String reviewerName;
    @Size(max = 2000, message = "审核意见不能超过2000个字符")
    private String reviewComment;
    private Integer version;
    private String sourceSystem;
    private String sourceKind;
    private String occurredAt;
    private String createTime;
    private String resolvedTime;

    public Long getRiskId()
    {
        return riskId;
    }

    public void setRiskId(Long riskId)
    {
        this.riskId = riskId;
    }

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

    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = realName;
    }

    public Integer getCreditScore()
    {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore)
    {
        this.creditScore = creditScore;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
    }

    public String getReviewerName()
    {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName)
    {
        this.reviewerName = reviewerName;
    }

    public String getReviewComment()
    {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment)
    {
        this.reviewComment = reviewComment;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
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

    public String getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(String createTime)
    {
        this.createTime = createTime;
    }

    public String getResolvedTime()
    {
        return resolvedTime;
    }

    public void setResolvedTime(String resolvedTime)
    {
        this.resolvedTime = resolvedTime;
    }
}
