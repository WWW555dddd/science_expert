package com.ruoyi.expert.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertInfo;
import com.ruoyi.expert.mapper.ExpertInfoMapper;
import com.ruoyi.expert.service.IExpertInfoService;

/**
 * 专家基础信息服务层处理
 *
 * @author science_expert
 */
@Service
public class ExpertInfoServiceImpl implements IExpertInfoService
{
    @Autowired
    private ExpertInfoMapper expertInfoMapper;

    @Override
    public List<ExpertInfo> selectExpertInfoList(ExpertInfo expertInfo)
    {
        return expertInfoMapper.selectExpertInfoList(expertInfo);
    }

    @Override
    public ExpertInfo selectExpertInfoByExpertId(Long expertId)
    {
        return expertInfoMapper.selectExpertInfoByExpertId(expertId);
    }

    @Override
    public boolean checkExpertNoUnique(ExpertInfo expertInfo)
    {
        Long expertId = StringUtils.isNull(expertInfo.getExpertId()) ? -1L : expertInfo.getExpertId();
        ExpertInfo info = expertInfoMapper.selectExpertInfoByExpertNo(expertInfo.getExpertNo());
        return StringUtils.isNull(info) || info.getExpertId().longValue() == expertId.longValue();
    }

    @Override
    public int insertExpertInfo(ExpertInfo expertInfo)
    {
        if (StringUtils.isEmpty(expertInfo.getStatus()))
        {
            expertInfo.setStatus("0");
        }
        return expertInfoMapper.insertExpertInfo(expertInfo);
    }

    @Override
    public int updateExpertInfo(ExpertInfo expertInfo)
    {
        if (StringUtils.isEmpty(expertInfo.getStatus()))
        {
            expertInfo.setStatus("0");
        }
        return expertInfoMapper.updateExpertInfo(expertInfo);
    }

    @Override
    public int deleteExpertInfoByExpertIds(Long[] expertIds)
    {
        return expertInfoMapper.deleteExpertInfoByExpertIds(expertIds);
    }
}
