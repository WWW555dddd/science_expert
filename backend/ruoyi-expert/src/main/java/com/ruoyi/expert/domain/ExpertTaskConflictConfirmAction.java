package com.ruoyi.expert.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 专家接单与任务冲突确认动作。
 */
public class ExpertTaskConflictConfirmAction
{
    @NotBlank(message = "接单结果不能为空")
    private String acceptanceStatus;

    @NotBlank(message = "冲突确认结果不能为空")
    private String conflictStatus;

    @NotBlank(message = "冲突确认说明不能为空")
    private String statement;

    @NotNull(message = "确认版本不能为空")
    private Integer version;

    public String getAcceptanceStatus()
    {
        return acceptanceStatus;
    }

    public void setAcceptanceStatus(String acceptanceStatus)
    {
        this.acceptanceStatus = acceptanceStatus;
    }

    public String getConflictStatus()
    {
        return conflictStatus;
    }

    public void setConflictStatus(String conflictStatus)
    {
        this.conflictStatus = conflictStatus;
    }

    public String getStatement()
    {
        return statement;
    }

    public void setStatement(String statement)
    {
        this.statement = statement;
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
