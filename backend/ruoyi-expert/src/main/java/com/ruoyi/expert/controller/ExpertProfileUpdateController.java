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
import com.ruoyi.expert.domain.ExpertProfileUpdateReviewRequest;
import com.ruoyi.expert.service.ExpertProfileUpdateService;

/**
 * 画像更新建议人工审核。
 */
@RestController
@RequestMapping("/expert/admin/profile-updates")
public class ExpertProfileUpdateController extends BaseController
{
    @Autowired
    private ExpertProfileUpdateService profileUpdateService;

    @PreAuthorize("@ss.hasPermi('expert:profile-update:review')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String status)
    {
        startPage();
        return getDataTable(profileUpdateService.list(status));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile-update:review')")
    @GetMapping("/{suggestionId}")
    public AjaxResult detail(@PathVariable Long suggestionId)
    {
        return success(profileUpdateService.detail(suggestionId));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile-update:review')")
    @PostMapping("/{suggestionId}/review")
    public AjaxResult review(@PathVariable Long suggestionId,
            @Validated @RequestBody ExpertProfileUpdateReviewRequest request)
    {
        return success(profileUpdateService.review(suggestionId, request,
                SecurityUtils.getLoginUser()));
    }
}
