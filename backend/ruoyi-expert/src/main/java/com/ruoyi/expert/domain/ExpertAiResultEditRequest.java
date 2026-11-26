package com.ruoyi.expert.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 审核人对AI提取值、画像评分或关系证据的修订。
 */
public class ExpertAiResultEditRequest
{
    @Size(max = 1000, message = "审核提取值不能超过1000个字符")
    private String reviewedValue;

    @Min(value = 0, message = "审核置信度不能小于0")
    @Max(value = 1, message = "审核置信度不能大于1")
    private Double reviewedConfidence;

    @Min(value = 0, message = "审核评分不能小于0")
    @Max(value = 100, message = "审核评分不能大于100")
    private Double reviewedScore;

    @Size(max = 1000, message = "审核证据不能超过1000个字符")
    private String reviewedEvidence;

    @Size(max = 1000, message = "审核关系证据不能超过1000个字符")
    private String reviewedExcerpt;

    @Size(max = 255, message = "审核源节点名称不能超过255个字符")
    private String reviewedSourceName;

    @Size(max = 64, message = "审核源节点类型不能超过64个字符")
    private String reviewedSourceType;

    @Size(max = 64, message = "审核关系类型不能超过64个字符")
    private String reviewedRelationType;

    @Size(max = 255, message = "审核目标节点名称不能超过255个字符")
    private String reviewedTargetName;

    @Size(max = 64, message = "审核目标节点类型不能超过64个字符")
    private String reviewedTargetType;

    @Size(max = 500, message = "审核意见不能超过500个字符")
    private String reviewComment;

    public String getReviewedValue()
    {
        return reviewedValue;
    }

    public void setReviewedValue(String reviewedValue)
    {
        this.reviewedValue = trim(reviewedValue);
    }

    public Double getReviewedConfidence()
    {
        return reviewedConfidence;
    }

    public void setReviewedConfidence(Double reviewedConfidence)
    {
        this.reviewedConfidence = reviewedConfidence;
    }

    public Double getReviewedScore()
    {
        return reviewedScore;
    }

    public void setReviewedScore(Double reviewedScore)
    {
        this.reviewedScore = reviewedScore;
    }

    public String getReviewedEvidence()
    {
        return reviewedEvidence;
    }

    public void setReviewedEvidence(String reviewedEvidence)
    {
        this.reviewedEvidence = trim(reviewedEvidence);
    }

    public String getReviewedExcerpt()
    {
        return reviewedExcerpt;
    }

    public void setReviewedExcerpt(String reviewedExcerpt)
    {
        this.reviewedExcerpt = trim(reviewedExcerpt);
    }

    public String getReviewedSourceName()
    {
        return reviewedSourceName;
    }

    public void setReviewedSourceName(String reviewedSourceName)
    {
        this.reviewedSourceName = trim(reviewedSourceName);
    }

    public String getReviewedSourceType()
    {
        return reviewedSourceType;
    }

    public void setReviewedSourceType(String reviewedSourceType)
    {
        this.reviewedSourceType = trim(reviewedSourceType);
    }

    public String getReviewedRelationType()
    {
        return reviewedRelationType;
    }

    public void setReviewedRelationType(String reviewedRelationType)
    {
        this.reviewedRelationType = trim(reviewedRelationType);
    }

    public String getReviewedTargetName()
    {
        return reviewedTargetName;
    }

    public void setReviewedTargetName(String reviewedTargetName)
    {
        this.reviewedTargetName = trim(reviewedTargetName);
    }

    public String getReviewedTargetType()
    {
        return reviewedTargetType;
    }

    public void setReviewedTargetType(String reviewedTargetType)
    {
        this.reviewedTargetType = trim(reviewedTargetType);
    }

    public String getReviewComment()
    {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment)
    {
        this.reviewComment = trim(reviewComment);
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
