package com.ruoyi.expert.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识图谱 Outbox 与 Neo4j 同步配置。
 */
@Component
@ConfigurationProperties(prefix = "expert.knowledge-graph")
public class ExpertKnowledgeGraphProperties
{
    private boolean enabled;
    private String neo4jUrl = "http://127.0.0.1:7474";
    private String neo4jUsername = "neo4j";
    private String neo4jPassword;
    private String database = "neo4j";
    private int batchSize = 20;

    public boolean isEnabled()
    {
        return enabled;
    }

    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    public String getNeo4jUrl()
    {
        return neo4jUrl;
    }

    public void setNeo4jUrl(String neo4jUrl)
    {
        this.neo4jUrl = neo4jUrl;
    }

    public String getNeo4jUsername()
    {
        return neo4jUsername;
    }

    public void setNeo4jUsername(String neo4jUsername)
    {
        this.neo4jUsername = neo4jUsername;
    }

    public String getNeo4jPassword()
    {
        return neo4jPassword;
    }

    public void setNeo4jPassword(String neo4jPassword)
    {
        this.neo4jPassword = neo4jPassword;
    }

    public String getDatabase()
    {
        return database;
    }

    public void setDatabase(String database)
    {
        this.database = database;
    }

    public int getBatchSize()
    {
        return batchSize;
    }

    public void setBatchSize(int batchSize)
    {
        this.batchSize = batchSize;
    }
}
