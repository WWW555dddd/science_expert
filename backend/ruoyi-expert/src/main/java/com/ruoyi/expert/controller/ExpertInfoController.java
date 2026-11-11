package com.ruoyi.expert.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.expert.domain.ExpertInfo;
import com.ruoyi.expert.service.IExpertInfoService;

/**
 * 专家基础信息Controller
 *
 * @author science_expert
 */
@RestController
@RequestMapping("/expert/info")
public class ExpertInfoController extends BaseController
{
    @Autowired
    private IExpertInfoService expertInfoService;

    /**
     * 查询专家基础信息列表
     */
    @PreAuthorize("@ss.hasPermi('expert:info:list')")
    @GetMapping("/list")
    public TableDataInfo list(ExpertInfo expertInfo)
    {
        startPage();
        List<ExpertInfo> list = expertInfoService.selectExpertInfoList(expertInfo);
        return getDataTable(list);
    }

    /**
     * 导出专家基础信息列表
     */
    @PreAuthorize("@ss.hasPermi('expert:info:export')")
    @Log(title = "专家信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ExpertInfo expertInfo)
    {
        List<ExpertInfo> list = expertInfoService.selectExpertInfoList(expertInfo);
        ExcelUtil<ExpertInfo> util = new ExcelUtil<ExpertInfo>(ExpertInfo.class);
        util.exportExcel(response, list, "专家信息数据");
    }

    /**
     * 获取专家基础信息详细信息
     */
    @PreAuthorize("@ss.hasPermi('expert:info:query')")
    @GetMapping(value = "/{expertId}")
    public AjaxResult getInfo(@PathVariable("expertId") Long expertId)
    {
        return success(expertInfoService.selectExpertInfoByExpertId(expertId));
    }

    /**
     * 新增专家基础信息
     */
    @PreAuthorize("@ss.hasPermi('expert:info:add')")
    @Log(title = "专家信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody ExpertInfo expertInfo)
    {
        if (!expertInfoService.checkExpertNoUnique(expertInfo))
        {
            return error("新增专家'" + expertInfo.getExpertName() + "'失败，专家编号已存在");
        }
        expertInfo.setCreateBy(getUsername());
        return toAjax(expertInfoService.insertExpertInfo(expertInfo));
    }

    /**
     * 修改专家基础信息
     */
    @PreAuthorize("@ss.hasPermi('expert:info:edit')")
    @Log(title = "专家信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody ExpertInfo expertInfo)
    {
        if (!expertInfoService.checkExpertNoUnique(expertInfo))
        {
            return error("修改专家'" + expertInfo.getExpertName() + "'失败，专家编号已存在");
        }
        expertInfo.setUpdateBy(getUsername());
        return toAjax(expertInfoService.updateExpertInfo(expertInfo));
    }

    /**
     * 删除专家基础信息
     */
    @PreAuthorize("@ss.hasPermi('expert:info:remove')")
    @Log(title = "专家信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{expertIds}")
    public AjaxResult remove(@PathVariable Long[] expertIds)
    {
        return toAjax(expertInfoService.deleteExpertInfoByExpertIds(expertIds));
    }
}
