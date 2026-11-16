package com.ruoyi.expert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 远程 Agent 配置。
 */
@Component
@ConfigurationProperties(prefix = "expert.agent")
public class ExpertAgentProperties
{
    private String mode = "local";
    private String baseUrl = "https://api.deepseek.com/chat/completions";
    private String apiKey;
    private String model = "deepseek-chat";
    private int timeoutSeconds = 90;
    private int maxInputChars = 30000;

    public boolean isRemoteConfigured()
    {
        return "remote".equalsIgnoreCase(mode) && apiKey != null && !apiKey.isBlank();
    }

    public String getMode()
    {
        return mode;
    }

    public void setMode(String mode)
    {
        this.mode = mode;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl)
    {
        this.baseUrl = baseUrl;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String apiKey)
    {
        this.apiKey = apiKey;
    }

    public String getModel()
    {
        return model;
    }

    public void setModel(String model)
    {
        this.model = model;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxInputChars()
    {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars)
    {
        this.maxInputChars = maxInputChars;
    }
}
