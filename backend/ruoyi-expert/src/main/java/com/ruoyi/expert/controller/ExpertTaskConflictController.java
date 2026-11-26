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
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmAction;
import com.ruoyi.expert.service.ExpertTaskConflictService;

/**
 * 专家接单与任务级利益冲突确认。
 */
@RestController
@RequestMapping("/expert/workspace/task-acceptances")
public class ExpertTaskConflictController extends BaseController
{
    @Autowired
    private ExpertTaskConflictService taskConflictService;

    @PreAuthorize("@ss.hasPermi('expert:task-acceptance:view')")
    @GetMapping
    public AjaxResult list(@RequestParam(required = false) String status)
    {
        return success(taskConflictService.listForExpert(
                SecurityUtils.getUserId(), status));
    }

    @PreAuthorize("@ss.hasPermi('expert:task-acceptance:confirm')")
    @PostMapping("/{confirmationId}/respond")
    public AjaxResult confirm(@PathVariable Long confirmationId,
            @Validated @RequestBody ExpertTaskConflictConfirmAction action)
    {
        return success(taskConflictService.confirm(
                confirmationId, SecurityUtils.getUserId(), action));
    }
}
