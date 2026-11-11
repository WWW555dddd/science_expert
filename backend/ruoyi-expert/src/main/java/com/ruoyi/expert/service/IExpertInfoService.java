package com.ruoyi.expert.service;

import java.util.List;
import com.ruoyi.expert.domain.ExpertInfo;

/**
 * 专家基础信息服务层
 *
 * @author science_expert
 */
public interface IExpertInfoService
{
    public List<ExpertInfo> selectExpertInfoList(ExpertInfo expertInfo);

    public ExpertInfo selectExpertInfoByExpertId(Long expertId);

    public boolean checkExpertNoUnique(ExpertInfo expertInfo);

    public int insertExpertInfo(ExpertInfo expertInfo);

    public int updateExpertInfo(ExpertInfo expertInfo);

    public int deleteExpertInfoByExpertIds(Long[] expertIds);
}
