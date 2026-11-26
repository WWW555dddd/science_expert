package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 接单状态申请审核参数。
 */
public class ExpertStatusReviewAction
{
    @NotBlank(message = "审核动作不能为空")
    private String action;

    @Size(max = 1000, message = "审核意见不能超过1000个字符")
    private String comment;

    private Integer version;

    public String getAction()
    {
        return action;
    }

    public void setAction(String action)
    {
        this.action = action;
    }

    public String getComment()
    {
        return comment;
    }

    public void setComment(String comment)
    {
        this.comment = comment;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }
}
