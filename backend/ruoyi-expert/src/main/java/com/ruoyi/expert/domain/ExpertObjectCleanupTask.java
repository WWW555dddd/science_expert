package com.ruoyi.expert.domain;

/**
 * MinIO 对象删除补偿任务。
 */
public class ExpertObjectCleanupTask
{
    private Long cleanupId;
    private String objectUri;
    private String taskStatus;
    private Integer attemptCount;

    public Long getCleanupId()
    {
        return cleanupId;
    }

    public void setCleanupId(Long cleanupId)
    {
        this.cleanupId = cleanupId;
    }

    public String getObjectUri()
    {
        return objectUri;
    }

    public void setObjectUri(String objectUri)
    {
        this.objectUri = objectUri;
    }

    public String getTaskStatus()
    {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus)
    {
        this.taskStatus = taskStatus;
    }

    public Integer getAttemptCount()
    {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount)
    {
        this.attemptCount = attemptCount;
    }
}
