package com.ruoyi.expert.domain;

import java.util.ArrayList;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员建档请求：专家总体信息与本次上传的认证材料。
 */
public class ExpertAdminSaveRequest
{
    @Valid
    @NotNull(message = "专家档案信息不能为空")
    private ExpertProfile profile;

    @Valid
    private List<ExpertMaterial> materials = new ArrayList<>();

    public ExpertProfile getProfile()
    {
        return profile;
    }

    public void setProfile(ExpertProfile profile)
    {
        this.profile = profile;
    }

    public List<ExpertMaterial> getMaterials()
    {
        return materials;
    }

    public void setMaterials(List<ExpertMaterial> materials)
    {
        this.materials = materials == null ? new ArrayList<>() : materials;
    }
}
