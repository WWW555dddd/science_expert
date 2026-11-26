package com.ruoyi.expert.domain;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 画像更新建议审核请求。
 */
public class ExpertProfileUpdateReviewRequest
{
    @NotBlank(message = "审核动作不能为空")
    private String action;

    @NotNull(message = "建议版本不能为空")
    private Integer version;

    private String comment;

    @Size(max = 2000, message = "审核修订摘要不能超过2000个字符")
    private String reviewedSummary;

    @Valid
    private List<ExpertProfileUpdateItemReview> items;

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public String getReviewedSummary()
    {
        return reviewedSummary;
    }

    public void setReviewedSummary(String reviewedSummary)
    {
        this.reviewedSummary = reviewedSummary == null ? null : reviewedSummary.trim();
    }

    public List<ExpertProfileUpdateItemReview> getItems()
    {
        return items;
    }

    public void setItems(List<ExpertProfileUpdateItemReview> items)
    {
        this.items = items;
    }
}
