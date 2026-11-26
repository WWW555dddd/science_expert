package com.ruoyi.expert.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;

/**
 * 专家资料文件。
 */
public class ExpertMaterial extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long materialId;
    private Long userId;
    private String userName;
    private String realName;
    private String fileName;
    private String filePath;
    private String fileType;
    private Long fileSize;
    private String materialCategory;
    private Integer versionNo;
    private String jobStatus;
    private String reviewStatus;
    private Integer extractedFields;
    private Integer issueCount;
    private String agentMessage;
    private String reviewComment;
    private String latestSubmitTime;
    private String effectiveFlag;
    private Integer fileCount;
    private String validFrom;
    private String validUntil;

    public Long getMaterialId()
    {
        return materialId;
    }

    public void setMaterialId(Long materialId)
    {
        this.materialId = materialId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = realName;
    }

    @NotBlank(message = "文件名不能为空")
    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    @NotBlank(message = "文件路径不能为空")
    public String getFilePath()
    {
        return filePath;
    }

    public void setFilePath(String filePath)
    {
        this.filePath = filePath;
    }

    public String getFileType()
    {
        return fileType;
    }

    public void setFileType(String fileType)
    {
        this.fileType = fileType;
    }

    public Long getFileSize()
    {
        return fileSize;
    }

    public void setFileSize(Long fileSize)
    {
        this.fileSize = fileSize;
    }

    public String getMaterialCategory()
    {
        return materialCategory;
    }

    public void setMaterialCategory(String materialCategory)
    {
        this.materialCategory = materialCategory;
    }

    public Integer getVersionNo()
    {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo)
    {
        this.versionNo = versionNo;
    }

    public String getJobStatus()
    {
        return jobStatus;
    }

    public void setJobStatus(String jobStatus)
    {
        this.jobStatus = jobStatus;
    }

    public String getReviewStatus()
    {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus)
    {
        this.reviewStatus = reviewStatus;
    }

    public Integer getExtractedFields()
    {
        return extractedFields;
    }

    public void setExtractedFields(Integer extractedFields)
    {
        this.extractedFields = extractedFields;
    }

    public Integer getIssueCount()
    {
        return issueCount;
    }

    public void setIssueCount(Integer issueCount)
    {
        this.issueCount = issueCount;
    }

    public String getAgentMessage()
    {
        return agentMessage;
    }

    public void setAgentMessage(String agentMessage)
    {
        this.agentMessage = agentMessage;
    }

    public String getReviewComment()
    {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment)
    {
        this.reviewComment = reviewComment;
    }

    public String getLatestSubmitTime()
    {
        return latestSubmitTime;
    }

    public void setLatestSubmitTime(String latestSubmitTime)
    {
        this.latestSubmitTime = latestSubmitTime;
    }

    public String getEffectiveFlag()
    {
        return effectiveFlag;
    }

    public void setEffectiveFlag(String effectiveFlag)
    {
        this.effectiveFlag = effectiveFlag;
    }

    public Integer getFileCount()
    {
        return fileCount;
    }

    public void setFileCount(Integer fileCount)
    {
        this.fileCount = fileCount;
    }

    public String getValidFrom()
    {
        return validFrom;
    }

    public void setValidFrom(String validFrom)
    {
        this.validFrom = validFrom;
    }

    public String getValidUntil()
    {
        return validUntil;
    }

    public void setValidUntil(String validUntil)
    {
        this.validUntil = validUntil;
    }
}
