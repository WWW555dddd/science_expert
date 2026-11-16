package com.ruoyi.expert.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertReviewAction;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.service.ExpertReviewService;
import com.ruoyi.expert.service.ExpertWorkspaceService;
import com.ruoyi.expert.service.ExpertMaterialPreviewService;
import org.springframework.http.ResponseEntity;

/**
 * 专家审核工作台。
 */
@RestController
@RequestMapping("/expert/review")
public class ExpertReviewController extends BaseController
{
    @Autowired
    private ExpertReviewService reviewService;

    @Autowired
    private ExpertWorkspaceService workspaceService;

    @Autowired
    private ExpertMaterialPreviewService previewService;

    @PreAuthorize("@ss.hasPermi('review:workspace:view')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(reviewService.summary());
    }

    @PreAuthorize("@ss.hasPermi('review:queue:list')")
    @GetMapping("/queue")
    public TableDataInfo queue(ExpertProfile profile)
    {
        startPage();
        Map<String, Object> query = new HashMap<>();
        query.put("realName", profile.getRealName());
        query.put("researchField", profile.getResearchField());
        query.put("reviewStatus", profile.getReviewStatus());
        return getDataTable(reviewService.queue(query));
    }

    @PreAuthorize("@ss.hasPermi('review:action')")
    @GetMapping("/{userId}")
    public AjaxResult detail(@PathVariable Long userId)
    {
        return success(reviewService.detail(userId));
    }

    @PreAuthorize("@ss.hasPermi('review:action')")
    @PostMapping("/{userId}/action")
    public AjaxResult action(@PathVariable Long userId, @Validated @RequestBody ExpertReviewAction action)
    {
        reviewService.action(userId, action, SecurityUtils.getLoginUser());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('review:approved:list')")
    @GetMapping("/approved/list")
    public TableDataInfo approved(ExpertProfile query)
    {
        startPage();
        Map<String, Object> params = new HashMap<>();
        params.put("realName", query.getRealName());
        params.put("researchField", query.getResearchField());
        params.put("serviceStatus", query.getServiceStatus());
        return getDataTable(reviewService.approved(params));
    }

    @PreAuthorize("@ss.hasPermi('review:agent:retry')")
    @PostMapping("/agent/{materialId}/retry")
    public AjaxResult retryAgent(@PathVariable Long materialId)
    {
        reviewService.retryAgent(materialId);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('review:action')")
    @GetMapping("/materials/{materialId}/preview")
    public ResponseEntity<byte[]> previewMaterial(@PathVariable Long materialId,
            @RequestParam(defaultValue = "raw") String mode)
    {
        ExpertMaterialPreviewService.PreviewFile preview = previewService.preview(materialId,
                null, false, "text".equalsIgnoreCase(mode));
        return ResponseEntity.ok()
                .header("Content-Type", preview.getContentType())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(preview.getBytes());
    }

    @PreAuthorize("@ss.hasPermi('review:action')")
    @PostMapping("/{userId}/status")
    public AjaxResult changeStatus(@PathVariable Long userId, @Validated @RequestBody ExpertStatusChange change)
    {
        workspaceService.changeServiceStatus(change, userId, SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('review:workspace:view')")
    @GetMapping("/jobs")
    public TableDataInfo jobs(ExpertMaterial query)
    {
        startPage();
        Map<String, Object> params = new HashMap<>();
        params.put("jobStatus", query.getJobStatus());
        return getDataTable(reviewService.jobs(params));
    }
}
