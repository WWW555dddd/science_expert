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
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertEligibilityRequest;
import com.ruoyi.expert.domain.ExpertRiskEventRequest;
import com.ruoyi.expert.domain.ExpertServiceEvidenceRequest;
import com.ruoyi.expert.domain.ExpertSnapshotBatchRequest;
import com.ruoyi.expert.service.ExpertIntegrationSnapshotService;
import com.ruoyi.expert.service.ExpertOpenApiService;
import com.ruoyi.expert.service.ExpertRiskReviewService;
import com.ruoyi.expert.service.ExpertTaskConflictService;
import com.ruoyi.expert.domain.ExpertTaskConflictConfirmation;

/**
 * 专家领域对业务系统提供的稳定接口。
 */
@RestController
@RequestMapping("/expert/open/v1")
public class ExpertOpenApiController extends BaseController
{
    @Autowired
    private ExpertOpenApiService openApiService;

    @Autowired
    private ExpertIntegrationSnapshotService snapshotService;

    @Autowired
    private ExpertRiskReviewService riskReviewService;

    @Autowired
    private ExpertTaskConflictService taskConflictService;

    @PreAuthorize("@ss.hasPermi('expert:open:query')")
    @GetMapping("/experts/{userId}/snapshot")
    public AjaxResult expertSnapshot(@PathVariable Long userId)
    {
        return success(snapshotService.snapshot(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:query')")
    @PostMapping("/experts/snapshots")
    public AjaxResult expertSnapshots(
            @Validated @RequestBody ExpertSnapshotBatchRequest request)
    {
        return success(snapshotService.batch(request.getUserIds()));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:query')")
    @PostMapping("/eligibility")
    public AjaxResult eligibility(
            @Validated @RequestBody ExpertEligibilityRequest request)
    {
        return success(openApiService.checkEligibility(request));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:evidence:write')")
    @PostMapping("/service-evidence")
    public AjaxResult ingestServiceEvidence(
            @Validated @RequestBody ExpertServiceEvidenceRequest request)
    {
        return success(openApiService.ingestServiceEvidence(request,
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:evidence:write')")
    @PostMapping("/risk-events")
    public AjaxResult ingestRiskEvent(
            @Validated @RequestBody ExpertRiskEventRequest request)
    {
        return success(riskReviewService.createEvent(request));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:evidence:write')")
    @PostMapping("/task-acceptance-requests")
    public AjaxResult requestTaskConflict(
            @Validated @RequestBody ExpertTaskConflictConfirmation request)
    {
        return success(taskConflictService.request(request,
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:evidence:write')")
    @PostMapping("/task-acceptances/{confirmationId}/confirm-arrangement")
    public AjaxResult confirmArrangement(@PathVariable Long confirmationId,
            @RequestParam(required = false) String sourceSystem)
    {
        return success(taskConflictService.confirmArrangement(
                confirmationId, SecurityUtils.getUsername(),
                StringUtils.isEmpty(sourceSystem)
                        ? "topic2-matching" : sourceSystem));
    }

    @PreAuthorize("@ss.hasPermi('expert:open:evidence:write')")
    @PostMapping("/task-acceptances/{confirmationId}/cancel-arrangement")
    public AjaxResult cancelArrangement(@PathVariable Long confirmationId,
            @RequestParam(required = false) String sourceSystem)
    {
        return success(taskConflictService.cancelArrangement(
                confirmationId, SecurityUtils.getUsername(),
                StringUtils.isEmpty(sourceSystem)
                        ? "topic2-matching" : sourceSystem));
    }
}
