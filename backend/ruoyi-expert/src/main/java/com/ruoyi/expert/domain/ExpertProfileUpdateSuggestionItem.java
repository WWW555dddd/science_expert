package com.ruoyi.expert.domain;

/**
 * 单维度画像更新建议。
 */
public class ExpertProfileUpdateSuggestionItem
{
    private Long itemId;
    private Long suggestionId;
    private String dimensionCode;
    private String dimensionName;
    private Double beforeScore;
    private Double suggestedScore;
    private Double approvedScore;
    private String reason;
    private String reviewedReason;
    private String evidence;
    private Double confidence;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Long getSuggestionId()
    {
        return suggestionId;
    }

    public void setSuggestionId(Long suggestionId)
    {
        this.suggestionId = suggestionId;
    }

    public String getDimensionCode()
    {
        return dimensionCode;
    }

    public void setDimensionCode(String dimensionCode)
    {
        this.dimensionCode = dimensionCode;
    }

    public String getDimensionName()
    {
        return dimensionName;
    }

    public void setDimensionName(String dimensionName)
    {
        this.dimensionName = dimensionName;
    }

    public Double getBeforeScore()
    {
        return beforeScore;
    }

    public void setBeforeScore(Double beforeScore)
    {
        this.beforeScore = beforeScore;
    }

    public Double getSuggestedScore()
    {
        return suggestedScore;
    }

    public void setSuggestedScore(Double suggestedScore)
    {
        this.suggestedScore = suggestedScore;
    }

    public Double getApprovedScore()
    {
        return approvedScore;
    }

    public void setApprovedScore(Double approvedScore)
    {
        this.approvedScore = approvedScore;
    }

    public String getReason()
    {
        return reason;
    }

    public void setReason(String reason)
    {
        this.reason = reason;
    }

    public String getReviewedReason()
    {
        return reviewedReason;
    }

    public void setReviewedReason(String reviewedReason)
    {
        this.reviewedReason = reviewedReason;
    }

    public String getEvidence()
    {
        return evidence;
    }

    public void setEvidence(String evidence)
    {
        this.evidence = evidence;
    }

    public Double getConfidence()
    {
        return confidence;
    }

    public void setConfidence(Double confidence)
    {
        this.confidence = confidence;
    }
}
