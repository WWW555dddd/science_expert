package com.ruoyi.expert.domain;

import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 外部业务核验快照。
 *
 * 该数据只表示业务系统或人工导入的业务事实，不表示课题一创建或派发了任务。
 */
public class ExpertBusinessContextSnapshot extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long contextId;
    private Long userId;
    private String externalTaskId;
    private String taskType;
    private String serviceStart;
    private String serviceEnd;
    private String serviceMode;
    private String serviceRegion;
    private String industry;
    private String occupancyStatus;
    private String conflictStatus;
    private String sourceSystem;
    private String sourceKind;
    private String sourceVersion;
    private String verifiedAt;

    public Long getContextId()
    {
        return contextId;
    }

    public void setContextId(Long contextId)
    {
        this.contextId = contextId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getExternalTaskId()
    {
        return externalTaskId;
    }

    public void setExternalTaskId(String externalTaskId)
    {
        this.externalTaskId = externalTaskId;
    }

    public String getTaskType()
    {
        return taskType;
    }

    public void setTaskType(String taskType)
    {
        this.taskType = taskType;
    }

    public String getServiceStart()
    {
        return serviceStart;
    }

    public void setServiceStart(String serviceStart)
    {
        this.serviceStart = serviceStart;
    }

    public String getServiceEnd()
    {
        return serviceEnd;
    }

    public void setServiceEnd(String serviceEnd)
    {
        this.serviceEnd = serviceEnd;
    }

    public String getServiceMode()
    {
        return serviceMode;
    }

    public void setServiceMode(String serviceMode)
    {
        this.serviceMode = serviceMode;
    }

    public String getServiceRegion()
    {
        return serviceRegion;
    }

    public void setServiceRegion(String serviceRegion)
    {
        this.serviceRegion = serviceRegion;
    }

    public String getIndustry()
    {
        return industry;
    }

    public void setIndustry(String industry)
    {
        this.industry = industry;
    }

    public String getOccupancyStatus()
    {
        return occupancyStatus;
    }

    public void setOccupancyStatus(String occupancyStatus)
    {
        this.occupancyStatus = occupancyStatus;
    }

    public String getConflictStatus()
    {
        return conflictStatus;
    }

    public void setConflictStatus(String conflictStatus)
    {
        this.conflictStatus = conflictStatus;
    }

    public String getSourceSystem()
    {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem)
    {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceKind()
    {
        return sourceKind;
    }

    public void setSourceKind(String sourceKind)
    {
        this.sourceKind = sourceKind;
    }

    public String getSourceVersion()
    {
        return sourceVersion;
    }

    public void setSourceVersion(String sourceVersion)
    {
        this.sourceVersion = sourceVersion;
    }

    public String getVerifiedAt()
    {
        return verifiedAt;
    }

    public void setVerifiedAt(String verifiedAt)
    {
        this.verifiedAt = verifiedAt;
    }
}
