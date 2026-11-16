package com.ruoyi.expert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 扫描材料和图片 OCR 配置。
 */
@Component
@ConfigurationProperties(prefix = "expert.ocr")
public class ExpertOcrProperties
{
    private String provider = "aliyun";
    private boolean enabled = true;
    private String endpoint = "ocr-api.cn-hangzhou.aliyuncs.com";
    private String region = "cn-hangzhou";
    private String accessKey;
    private String secretKey;
    private String defaultType = "Advanced";
    private int maxPages = 30;
    private int timeoutSeconds = 90;

    public boolean isAliyunConfigured()
    {
        return "aliyun".equalsIgnoreCase(provider)
                && notBlank(endpoint) && notBlank(region) && notBlank(accessKey) && notBlank(secretKey);
    }

    private static boolean notBlank(String value)
    {
        return value != null && !value.isBlank();
    }

    public String getProvider()
    {
        return provider;
    }

    public void setProvider(String provider)
    {
        this.provider = provider;
    }

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }

    public String getAccessKey()
    {
        return accessKey;
    }

    public void setAccessKey(String accessKey)
    {
        this.accessKey = accessKey;
    }

    public String getSecretKey()
    {
        return secretKey;
    }

    public void setSecretKey(String secretKey)
    {
        this.secretKey = secretKey;
    }

    public String getDefaultType()
    {
        return defaultType;
    }

    public void setDefaultType(String defaultType)
    {
        this.defaultType = defaultType;
    }

    public int getMaxPages()
    {
        return maxPages;
    }

    public void setMaxPages(int maxPages)
    {
        this.maxPages = maxPages;
    }

    public int getTimeoutSeconds()
    {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds)
    {
        this.timeoutSeconds = timeoutSeconds;
    }
}
