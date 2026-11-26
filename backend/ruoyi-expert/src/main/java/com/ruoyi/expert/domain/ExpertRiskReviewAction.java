package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 风险预警审核动作。
 */
public class ExpertRiskReviewAction
{
    @NotBlank(message = "审核动作不能为空")
    private String action;

    @NotNull(message = "风险预警版本不能为空")
    private Integer version;

    private String comment;

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
}
