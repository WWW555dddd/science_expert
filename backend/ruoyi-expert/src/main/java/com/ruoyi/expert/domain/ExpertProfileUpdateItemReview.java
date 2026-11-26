package com.ruoyi.expert.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 单维度审核分值。
 */
public class ExpertProfileUpdateItemReview
{
    @NotNull(message = "建议明细ID不能为空")
    private Long itemId;

    @NotNull(message = "审核分值不能为空")
    @Min(value = 0, message = "画像评分不能小于0")
    @Max(value = 100, message = "画像评分不能大于100")
    private Double approvedScore;

    @Size(max = 1000, message = "审核修订理由不能超过1000个字符")
    private String reviewedReason;

    public Long getItemId()
    {
        return itemId;
    }

    public void setItemId(Long itemId)
    {
        this.itemId = itemId;
    }

    public Double getApprovedScore()
    {
        return approvedScore;
    }

    public void setApprovedScore(Double approvedScore)
    {
        this.approvedScore = approvedScore;
    }

    public String getReviewedReason()
    {
        return reviewedReason;
    }

    public void setReviewedReason(String reviewedReason)
    {
        this.reviewedReason = reviewedReason == null ? null : reviewedReason.trim();
    }
}
