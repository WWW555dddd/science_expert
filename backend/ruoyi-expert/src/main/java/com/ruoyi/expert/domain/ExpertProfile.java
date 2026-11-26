package com.ruoyi.expert.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 专家档案。
 */
public class ExpertProfile extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    private Long profileId;
    private Long userId;
    private String userName;
    private String nickName;
    private String realName;
    private String organization;
    private String professionalTitle;
    private String researchField;
    private String industryField;
    private String serviceRegion;
    private String phone;
    private String email;
    private String careerSummary;
    private String reviewStatus;
    private String serviceStatus;
    private Integer creditScore;
    private Integer profileCompletion;
    private Integer graphVersion;
    private Integer version;
    private String delFlag;
    private Integer materialCount;
    private Integer pendingReviewCount;
    private Integer approvedCount;
    private String latestSubmitTime;
    private String approvedTime;

    public Long getProfileId()
    {
        return profileId;
    }

    public void setProfileId(Long profileId)
    {
        this.profileId = profileId;
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

    public String getNickName()
    {
        return nickName;
    }

    public void setNickName(String nickName)
    {
        this.nickName = nickName;
    }

    @NotBlank(message = "专家姓名不能为空")
    @Size(max = 64, message = "专家姓名不能超过64个字符")
    public String getRealName()
    {
        return realName;
    }

    public void setRealName(String realName)
    {
        this.realName = trim(realName);
    }

    @Size(max = 128, message = "所属单位不能超过128个字符")
    public String getOrganization()
    {
        return organization;
    }

    public void setOrganization(String organization)
    {
        this.organization = trim(organization);
    }

    @Size(max = 64, message = "职称职务不能超过64个字符")
    public String getProfessionalTitle()
    {
        return professionalTitle;
    }

    public void setProfessionalTitle(String professionalTitle)
    {
        this.professionalTitle = trim(professionalTitle);
    }

    @Size(max = 255, message = "专业方向不能超过255个字符")
    public String getResearchField()
    {
        return researchField;
    }

    public void setResearchField(String researchField)
    {
        this.researchField = trim(researchField);
    }

    @Size(max = 255, message = "产业领域不能超过255个字符")
    public String getIndustryField()
    {
        return industryField;
    }

    public void setIndustryField(String industryField)
    {
        this.industryField = trim(industryField);
    }

    @Size(max = 128, message = "服务区域不能超过128个字符")
    public String getServiceRegion()
    {
        return serviceRegion;
    }

    public void setServiceRegion(String serviceRegion)
    {
        this.serviceRegion = trim(serviceRegion);
    }

    @Size(max = 32, message = "联系电话不能超过32个字符")
    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = trim(phone);
    }

    @Email(message = "电子邮箱格式不正确")
    @Size(max = 128, message = "电子邮箱不能超过128个字符")
    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = trim(email);
    }

    @Size(max = 2000, message = "专家简介不能超过2000个字符")
    public String getCareerSummary()
    {
        return careerSummary;
    }

    public void setCareerSummary(String careerSummary)
    {
        this.careerSummary = trim(careerSummary);
    }

    public String getReviewStatus()
    {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus)
    {
        this.reviewStatus = reviewStatus;
    }

    public String getServiceStatus()
    {
        return serviceStatus;
    }

    public void setServiceStatus(String serviceStatus)
    {
        this.serviceStatus = serviceStatus;
    }

    public Integer getCreditScore()
    {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore)
    {
        this.creditScore = creditScore;
    }

    public Integer getProfileCompletion()
    {
        return profileCompletion;
    }

    public void setProfileCompletion(Integer profileCompletion)
    {
        this.profileCompletion = profileCompletion;
    }

    public Integer getGraphVersion()
    {
        return graphVersion;
    }

    public void setGraphVersion(Integer graphVersion)
    {
        this.graphVersion = graphVersion;
    }

    public Integer getVersion()
    {
        return version;
    }

    public void setVersion(Integer version)
    {
        this.version = version;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }

    public Integer getMaterialCount()
    {
        return materialCount;
    }

    public void setMaterialCount(Integer materialCount)
    {
        this.materialCount = materialCount;
    }

    public Integer getPendingReviewCount()
    {
        return pendingReviewCount;
    }

    public void setPendingReviewCount(Integer pendingReviewCount)
    {
        this.pendingReviewCount = pendingReviewCount;
    }

    public Integer getApprovedCount()
    {
        return approvedCount;
    }

    public void setApprovedCount(Integer approvedCount)
    {
        this.approvedCount = approvedCount;
    }

    public String getLatestSubmitTime()
    {
        return latestSubmitTime;
    }

    public void setLatestSubmitTime(String latestSubmitTime)
    {
        this.latestSubmitTime = latestSubmitTime;
    }

    public String getApprovedTime()
    {
        return approvedTime;
    }

    public void setApprovedTime(String approvedTime)
    {
        this.approvedTime = approvedTime;
    }

    private static String trim(String value)
    {
        return value == null ? null : value.trim();
    }
}
