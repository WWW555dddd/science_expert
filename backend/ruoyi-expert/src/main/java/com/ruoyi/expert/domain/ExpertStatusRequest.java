package com.ruoyi.expert.domain;

/**
 * 专家接单状态变更申请。
 */
public class ExpertStatusRequest
{
    private Long requestId;

    private Long userId;

    private String userName;

    private String realName;

    private String sourceStatus;

    private String targetStatus;

    private String requestStatus;

    private String reason;

    private String creditRelated;

    private Long requesterId;

    private String requesterName;

    private Long reviewerId;

    private String reviewerName;

    private String reviewComment;

    private Integer version;

    private String createTime;

    private String updateTime;

    private String resolvedTime;

    public Long getRequestId()
    {
        return requestId;
    }

    public void setRequestId(Long requestId)
    {
        this.requestId = requestId;
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

    public String getSourceStatus()
    {
        return sourceStatus;
    }

    public void setSourceStatus(String sourceStatus)
    {
        this.sourceStatus = sourceStatus;
    }

    public String getTargetStatus()
    {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus)
    {
        this.targetStatus = targetStatus;
    }

    public String getRequestStatus()
    {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus)
    {
        this.requestStatus = requestStatus;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getCreditRelated()
    {
        return creditRelated;
    }

    public void setCreditRelated(String creditRelated)
    {
        this.creditRelated = creditRelated;
    }

    public Long getRequesterId()
    {
        return requesterId;
    }

    public void setRequesterId(Long requesterId)
    {
        this.requesterId = requesterId;
    }

    public String getRequesterName()
    {
        return requesterName;
    }

    public void setRequesterName(String requesterName)
    {
        this.requesterName = requesterName;
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

    public String getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(String createTime)
    {
        this.createTime = createTime;
    }

    public String getUpdateTime()
    {
        return updateTime;
    }

    public void setUpdateTime(String updateTime)
    {
        this.updateTime = updateTime;
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
