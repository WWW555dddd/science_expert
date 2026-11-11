package com.ruoyi.expert.domain;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 专家基础信息对象 expert_info
 *
 * @author science_expert
 */
public class ExpertInfo extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 专家ID */
    @Excel(name = "专家ID", cellType = ColumnType.NUMERIC)
    private Long expertId;

    /** 专家编号 */
    @Excel(name = "专家编号")
    private String expertNo;

    /** 专家姓名 */
    @Excel(name = "专家姓名")
    private String expertName;

    /** 所属单位 */
    @Excel(name = "所属单位")
    private String organization;

    /** 职称职务 */
    @Excel(name = "职称职务")
    private String professionalTitle;

    /** 专业方向 */
    @Excel(name = "专业方向")
    private String researchField;

    /** 产业领域 */
    @Excel(name = "产业领域")
    private String industryField;

    /** 服务区域 */
    @Excel(name = "服务区域")
    private String serviceRegion;

    /** 联系电话 */
    @Excel(name = "联系电话")
    private String phone;

    /** 电子邮箱 */
    @Excel(name = "电子邮箱")
    private String email;

    /** 状态（0正常 1停用） */
    @Excel(name = "状态", readConverterExp = "0=正常,1=停用")
    private String status;

    public Long getExpertId()
    {
        return expertId;
    }

    public void setExpertId(Long expertId)
    {
        this.expertId = expertId;
    }

    @NotBlank(message = "专家编号不能为空")
    @Size(max = 32, message = "专家编号长度不能超过32个字符")
    public String getExpertNo()
    {
        return expertNo;
    }

    public void setExpertNo(String expertNo)
    {
        this.expertNo = normalize(expertNo);
    }

    @NotBlank(message = "专家姓名不能为空")
    @Size(max = 64, message = "专家姓名长度不能超过64个字符")
    public String getExpertName()
    {
        return expertName;
    }

    public void setExpertName(String expertName)
    {
        this.expertName = normalize(expertName);
    }

    @Size(max = 128, message = "所属单位长度不能超过128个字符")
    public String getOrganization()
    {
        return organization;
    }

    public void setOrganization(String organization)
    {
        this.organization = normalize(organization);
    }

    @Size(max = 64, message = "职称职务长度不能超过64个字符")
    public String getProfessionalTitle()
    {
        return professionalTitle;
    }

    public void setProfessionalTitle(String professionalTitle)
    {
        this.professionalTitle = normalize(professionalTitle);
    }

    @Size(max = 255, message = "专业方向长度不能超过255个字符")
    public String getResearchField()
    {
        return researchField;
    }

    public void setResearchField(String researchField)
    {
        this.researchField = normalize(researchField);
    }

    @Size(max = 255, message = "产业领域长度不能超过255个字符")
    public String getIndustryField()
    {
        return industryField;
    }

    public void setIndustryField(String industryField)
    {
        this.industryField = normalize(industryField);
    }

    @Size(max = 128, message = "服务区域长度不能超过128个字符")
    public String getServiceRegion()
    {
        return serviceRegion;
    }

    public void setServiceRegion(String serviceRegion)
    {
        this.serviceRegion = normalize(serviceRegion);
    }

    @Pattern(regexp = "^$|^[0-9+()\\-\\s]{6,32}$", message = "联系电话格式不正确")
    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = normalize(phone);
    }

    @Email(message = "电子邮箱格式不正确")
    @Size(max = 128, message = "电子邮箱长度不能超过128个字符")
    public String getEmail()
    {
        return email;
    }

    public void setEmail(String email)
    {
        this.email = normalize(email);
    }

    @Pattern(regexp = "^[01]$", message = "专家状态只能是0或1")
    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = normalize(status);
    }

    @Size(max = 500, message = "备注长度不能超过500个字符")
    @Override
    public String getRemark()
    {
        return super.getRemark();
    }

    private static String normalize(String value)
    {
        return value == null ? null : value.trim();
    }

    @Override
    public String toString()
    {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("expertId", getExpertId())
            .append("expertNo", getExpertNo())
            .append("expertName", getExpertName())
            .append("organization", getOrganization())
            .append("professionalTitle", getProfessionalTitle())
            .append("researchField", getResearchField())
            .append("industryField", getIndustryField())
            .append("serviceRegion", getServiceRegion())
            .append("phone", getPhone())
            .append("email", getEmail())
            .append("status", getStatus())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
