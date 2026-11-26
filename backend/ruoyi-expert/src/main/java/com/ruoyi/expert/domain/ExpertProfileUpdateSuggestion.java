package com.ruoyi.expert.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务事实驱动的画像更新建议。
 */
public class ExpertProfileUpdateSuggestion
{
    private Long suggestionId;
    private Long evidenceId;
    private Long userId;
    private String realName;
    private String status;
    private String summary;
    private String reviewedSummary;
    private Double confidence;
    private String generationMode;
    private String promptVersion;
    private String modelName;
    private String agentOutputJson;
    private Long reviewerId;
    private String reviewerName;
    private String reviewComment;
    private Long releaseId;
    private Integer version;
    private String createTime;
    private String resolvedTime;
    private List<ExpertProfileUpdateSuggestionItem> items = new ArrayList<>();

    public Long getSuggestionId()
    {
        return suggestionId;
    }

    public void setSuggestionId(Long suggestionId)
    {
        this.suggestionId = suggestionId;
    }

    public Long getEvidenceId()
    {
        return evidenceId;
    }

    public void setEvidenceId(Long evidenceId)
    {
        this.evidenceId = evidenceId;
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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getSummary()
    {
        return summary;
    }

    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    public String getReviewedSummary()
    {
        return reviewedSummary;
    }

    public void setReviewedSummary(String reviewedSummary)
    {
        this.reviewedSummary = reviewedSummary;
    }

    public Double getConfidence()
    {
        return confidence;
    }

    public void setConfidence(Double confidence)
    {
        this.confidence = confidence;
    }

    public String getGenerationMode()
    {
        return generationMode;
    }

    public void setGenerationMode(String generationMode)
    {
        this.generationMode = generationMode;
    }

    public String getPromptVersion()
    {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion)
    {
        this.promptVersion = promptVersion;
    }

    public String getModelName()
    {
        return modelName;
    }

    public void setModelName(String modelName)
    {
        this.modelName = modelName;
    }

    public String getAgentOutputJson()
    {
        return agentOutputJson;
    }

    public void setAgentOutputJson(String agentOutputJson)
    {
        this.agentOutputJson = agentOutputJson;
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

    public Long getReleaseId()
    {
        return releaseId;
    }

    public void setReleaseId(Long releaseId)
    {
        this.releaseId = releaseId;
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

    public String getResolvedTime()
    {
        return resolvedTime;
    }

    public void setResolvedTime(String resolvedTime)
    {
        this.resolvedTime = resolvedTime;
    }

    public List<ExpertProfileUpdateSuggestionItem> getItems()
    {
        return items;
    }

    public void setItems(List<ExpertProfileUpdateSuggestionItem> items)
    {
        this.items = items;
    }
}
