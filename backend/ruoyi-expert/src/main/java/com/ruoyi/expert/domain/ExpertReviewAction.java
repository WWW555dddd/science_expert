package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;

/**
 * 审核操作参数。
 */
public class ExpertReviewAction
{
    private Long materialId;

    @NotBlank(message = "审核动作不能为空")
    private String action;

    private String comment;

    public Long getMaterialId()
    {
        return materialId;
    }

    public void setMaterialId(Long materialId)
    {
        this.materialId = materialId;
    }

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
}
