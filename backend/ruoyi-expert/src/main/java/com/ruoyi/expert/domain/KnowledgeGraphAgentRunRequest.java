package com.ruoyi.expert.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * GraphRAG Agent 运行请求。
 */
public class KnowledgeGraphAgentRunRequest
{
    @NotBlank(message = "任务目标不能为空")
    @Size(max = 4000, message = "任务目标不能超过4000字符")
    private String objective;

    @Min(value = 1, message = "maxSteps 不能小于1")
    @Max(value = 10, message = "maxSteps 不能大于10")
    private Integer maxSteps = 6;

    @Size(max = 10, message = "服务日期格式应为 YYYY-MM-DD")
    private String serviceDate;

    @Size(max = 128, message = "服务区域不能超过128字符")
    private String serviceRegion;

    @Size(max = 16, message = "服务方式不能超过16字符")
    private String serviceMode;

    @Size(max = 128, message = "目标产业不能超过128字符")
    private String industry;

    @Size(max = 1000, message = "能力要求不能超过1000字符")
    private String requiredCapabilities;

    public String getObjective()
    {
        return objective;
    }

    public void setObjective(String objective)
    {
        this.objective = objective;
    }

    public Integer getMaxSteps()
    {
        return maxSteps;
    }

    public void setMaxSteps(Integer maxSteps)
    {
        this.maxSteps = maxSteps;
    }

    public String getServiceDate()
    {
        return serviceDate;
    }

    public void setServiceDate(String serviceDate)
    {
        this.serviceDate = serviceDate;
    }

    public String getServiceRegion()
    {
        return serviceRegion;
    }

    public void setServiceRegion(String serviceRegion)
    {
        this.serviceRegion = serviceRegion;
    }

    public String getServiceMode()
    {
        return serviceMode;
    }

    public void setServiceMode(String serviceMode)
    {
        this.serviceMode = serviceMode;
    }

    public String getIndustry()
    {
        return industry;
    }

    public void setIndustry(String industry)
    {
        this.industry = industry;
    }

    public String getRequiredCapabilities()
    {
        return requiredCapabilities;
    }

    public void setRequiredCapabilities(String requiredCapabilities)
    {
        this.requiredCapabilities = requiredCapabilities;
    }
}
