package com.ruoyi.expert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 专家资料对象存储配置。
 */
@Component
@ConfigurationProperties(prefix = "expert.storage")
public class ExpertStorageProperties
{
    private String type = "minio";
    private String endpoint = "http://127.0.0.1:9000";
    private String accessKey;
    private String secretKey;
    private String bucket = "science-expert";
    private String region = "us-east-1";

    public boolean isMinioConfigured()
    {
        return "minio".equalsIgnoreCase(type)
                && hasText(endpoint) && hasText(accessKey) && hasText(secretKey) && hasText(bucket);
    }

    private static boolean hasText(String value)
    {
        return value != null && !value.isBlank();
    }

    public String getType()
    {
        return type;
    }

    public void setType(String type)
    {
        this.type = type;
    }

    public String getEndpoint()
    {
        return endpoint;
    }

    public void setEndpoint(String endpoint)
    {
        this.endpoint = endpoint;
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

    public String getBucket()
    {
        return bucket;
    }

    public void setBucket(String bucket)
    {
        this.bucket = bucket;
    }

    public String getRegion()
    {
        return region;
    }

    public void setRegion(String region)
    {
        this.region = region;
    }
}
