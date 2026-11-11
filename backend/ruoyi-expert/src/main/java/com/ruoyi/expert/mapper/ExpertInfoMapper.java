package com.ruoyi.expert.mapper;

import java.util.List;
import com.ruoyi.expert.domain.ExpertInfo;

/**
 * 专家基础信息数据层
 *
 * @author science_expert
 */
public interface ExpertInfoMapper
{
    public List<ExpertInfo> selectExpertInfoList(ExpertInfo expertInfo);

    public ExpertInfo selectExpertInfoByExpertId(Long expertId);

    public ExpertInfo selectExpertInfoByExpertNo(String expertNo);

    public int insertExpertInfo(ExpertInfo expertInfo);

    public int updateExpertInfo(ExpertInfo expertInfo);

    public int deleteExpertInfoByExpertId(Long expertId);

    public int deleteExpertInfoByExpertIds(Long[] expertIds);
}
