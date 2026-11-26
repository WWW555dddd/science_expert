package com.ruoyi.expert.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.expert.domain.ExpertRiskReviewAction;
import com.ruoyi.expert.service.ExpertRiskReviewService;

/**
 * 投诉、低评价和信用预警审核。
 */
@RestController
@RequestMapping("/expert/admin/risk-reviews")
public class ExpertRiskReviewController extends BaseController
{
    @Autowired
    private ExpertRiskReviewService riskReviewService;

    @PreAuthorize("@ss.hasPermi('expert:risk-review:review')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String status)
    {
        startPage();
        return getDataTable(riskReviewService.list(status));
    }

    @PreAuthorize("@ss.hasPermi('expert:risk-review:review')")
    @GetMapping("/{riskId}")
    public AjaxResult detail(@PathVariable Long riskId)
    {
        return success(riskReviewService.detail(riskId));
    }

    @PreAuthorize("@ss.hasPermi('expert:risk-review:review')")
    @PostMapping("/{riskId}/review")
    public AjaxResult review(@PathVariable Long riskId,
            @Validated @RequestBody ExpertRiskReviewAction action)
    {
        return success(riskReviewService.review(
                riskId, action, SecurityUtils.getLoginUser()));
    }
}
