package com.ruoyi.expert.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmation;
import com.ruoyi.expert.service.ExpertBusinessService;
import com.ruoyi.expert.service.ExpertTaskConflictService;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 业务经办人员专家查询工作台。
 */
@RestController
@RequestMapping("/expert/business")
public class ExpertBusinessController extends BaseController
{
    @Autowired
    private ExpertBusinessService businessService;

    @Autowired
    private ExpertTaskConflictService taskConflictService;

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @GetMapping("/experts")
    public TableDataInfo experts(String keyword, String realName, String organization,
            String serviceStatus)
    {
        startPage();
        Map<String, Object> query = new HashMap<>();
        query.put("keyword", keyword);
        query.put("realName", realName);
        query.put("organization", organization);
        query.put("serviceStatus", serviceStatus);
        return getDataTable(businessService.listExperts(query));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @GetMapping("/experts/{userId}")
    public AjaxResult expertDetail(@PathVariable Long userId)
    {
        return success(businessService.expertDetail(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @GetMapping("/experts/{userId}/occupancy")
    public AjaxResult occupancy(@PathVariable Long userId,
            @RequestParam String serviceStart, @RequestParam String serviceEnd)
    {
        return success(businessService.occupancySnapshots(
                userId, serviceStart, serviceEnd));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @GetMapping("/experts/{userId}/task-acceptances")
    public AjaxResult taskAcceptances(@PathVariable Long userId,
            @RequestParam(required = false) String status)
    {
        return success(taskConflictService.listForExpert(userId, status));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @PostMapping("/eligibility")
    public AjaxResult eligibility(
            @Validated @RequestBody ExpertEligibilityRequest request)
    {
        return success(businessService.checkTaskEligibility(request));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @PostMapping("/task-acceptance-requests")
    public AjaxResult requestTaskConflict(
            @Validated @RequestBody ExpertTaskConflictConfirmation request)
    {
        businessService.ensureAcceptanceRequestEligible(toEligibilityRequest(request));
        return success(taskConflictService.request(request,
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @PostMapping("/task-acceptances/{confirmationId}/confirm-arrangement")
    public AjaxResult confirmArrangement(@PathVariable Long confirmationId)
    {
        return success(taskConflictService.confirmArrangement(
                confirmationId, SecurityUtils.getUsername(), "business-workspace"));
    }

    @PreAuthorize("@ss.hasPermi('expert:business:view')")
    @PostMapping("/task-acceptances/{confirmationId}/cancel-arrangement")
    public AjaxResult cancelArrangement(@PathVariable Long confirmationId)
    {
        return success(taskConflictService.cancelArrangement(
                confirmationId, SecurityUtils.getUsername(), "business-workspace"));
    }

    private static ExpertEligibilityRequest toEligibilityRequest(
            ExpertTaskConflictConfirmation request)
    {
        ExpertEligibilityRequest result = new ExpertEligibilityRequest();
        result.setUserId(request.getUserId());
        result.setExternalTaskId(request.getExternalTaskId());
        result.setServiceStart(request.getServiceStart());
        result.setServiceEnd(request.getServiceEnd());
        result.setServiceMode(request.getServiceMode());
        result.setServiceRegion(request.getServiceRegion());
        result.setIndustry(request.getIndustry());
        return result;
    }
}
