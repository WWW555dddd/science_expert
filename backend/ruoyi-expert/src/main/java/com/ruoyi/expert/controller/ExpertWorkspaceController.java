package com.ruoyi.expert.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;
import com.ruoyi.expert.service.ExpertWorkspaceService;
import com.ruoyi.expert.service.ExpertMaterialPreviewService;
import com.ruoyi.expert.service.ExpertProfileWordImportService;
import com.ruoyi.expert.service.ExpertProfileUpdateService;
import com.ruoyi.expert.service.ExpertRiskReviewService;
import com.ruoyi.expert.service.ExpertVersionService;

/**
 * 专家工作台。
 */
@RestController
@RequestMapping("/expert/workspace")
public class ExpertWorkspaceController extends BaseController
{
    private static final Set<String> ALLOWED_MATERIAL_EXTENSIONS =
            Set.of("pdf", "docx", "txt", "md", "csv", "png", "jpg", "jpeg");

    @Autowired
    private ExpertWorkspaceService workspaceService;

    @Autowired
    private com.ruoyi.expert.service.ExpertObjectStorage objectStorage;

    @Autowired
    private ExpertMaterialPreviewService previewService;

    @Autowired
    private ExpertProfileWordImportService profileWordImportService;

    @Autowired
    private ExpertVersionService versionService;

    @Autowired
    private ExpertProfileUpdateService profileUpdateService;

    @Autowired
    private ExpertRiskReviewService riskReviewService;

    @PreAuthorize("@ss.hasPermi('expert:workspace:view')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(workspaceService.summary(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:view')")
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        return success(workspaceService.getProfile(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @PutMapping("/profile")
    public AjaxResult saveProfile(@Validated @RequestBody ExpertProfile profile)
    {
        return success(workspaceService.saveProfile(profile, SecurityUtils.getUserId(),
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:view')")
    @GetMapping("/profile/import-template")
    public ResponseEntity<byte[]> downloadProfileImportTemplate()
    {
        return wordTemplateResponse(profileWordImportService.createTemplate());
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @PostMapping(value = "/profile/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult importProfile(@RequestParam("file") MultipartFile file)
    {
        return success(profileWordImportService.importProfile(file));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:view')")
    @GetMapping("/schedules")
    public AjaxResult schedules()
    {
        return success(workspaceService.getSchedules(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @PutMapping("/schedules")
    public AjaxResult saveSchedules(@RequestBody List<ExpertSchedule> schedules)
    {
        workspaceService.saveSchedules(schedules, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:view')")
    @GetMapping("/unavailable-periods")
    public AjaxResult unavailablePeriods()
    {
        return success(workspaceService.getUnavailablePeriods(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:view')")
    @GetMapping("/occupancy-periods")
    public AjaxResult occupancyPeriods(@RequestParam String serviceStart,
            @RequestParam String serviceEnd)
    {
        return success(workspaceService.getOccupancyPeriods(
                SecurityUtils.getUserId(), serviceStart, serviceEnd));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @PostMapping("/unavailable-periods")
    public AjaxResult createUnavailablePeriod(
            @RequestBody ExpertUnavailablePeriod period)
    {
        period.setUnavailableId(null);
        return success(workspaceService.saveUnavailablePeriod(period,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @PutMapping("/unavailable-periods/{unavailableId}")
    public AjaxResult updateUnavailablePeriod(@PathVariable Long unavailableId,
            @RequestBody ExpertUnavailablePeriod period)
    {
        period.setUnavailableId(unavailableId);
        return success(workspaceService.saveUnavailablePeriod(period,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:profile:edit')")
    @DeleteMapping("/unavailable-periods/{unavailableId}")
    public AjaxResult deleteUnavailablePeriod(@PathVariable Long unavailableId)
    {
        workspaceService.deleteUnavailablePeriod(unavailableId, SecurityUtils.getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/materials")
    public TableDataInfo materials(ExpertMaterial query)
    {
        startPage();
        return getDataTable(workspaceService.listMaterials(query, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/certifications")
    public AjaxResult certifications()
    {
        return success(workspaceService.certifications(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @PostMapping(value = "/materials/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadMaterial(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            return error("请选择需要上传的文件");
        }
        if (file.getSize() > 10L * 1024 * 1024)
        {
            return error("单个文件不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_MATERIAL_EXTENSIONS.contains(extension))
        {
            return error("不支持该文件类型，请上传PDF、DOCX、TXT、CSV、MD或图片");
        }
        String objectUri;
        try
        {
            objectUri = objectStorage.upload(SecurityUtils.getUserId(), originalFilename,
                    file.getContentType(), file.getSize(), file.getInputStream());
        }
        catch (Exception ex)
        {
            return error(ex.getMessage());
        }
        AjaxResult result = success();
        result.put("filePath", objectUri);
        result.put("fileName", originalFilename);
        result.put("fileSize", file.getSize());
        result.put("contentType", file.getContentType());
        return result;
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @PostMapping("/materials")
    public AjaxResult submitMaterial(@Validated @RequestBody ExpertMaterial material)
    {
        return success(workspaceService.submitMaterial(material, SecurityUtils.getUserId(),
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/materials/{materialId}/files")
    public AjaxResult materialFiles(@PathVariable Long materialId)
    {
        return success(workspaceService.listMaterialFiles(materialId, SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/qualification-reminders")
    public AjaxResult qualificationReminders()
    {
        return success(workspaceService.qualificationReminders(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @PostMapping("/materials/{materialId}/files")
    public AjaxResult addMaterialFile(@PathVariable Long materialId,
            @Validated @RequestBody ExpertMaterialFile file)
    {
        return success(workspaceService.addMaterialFile(materialId, file,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @PutMapping("/materials/{materialId}/validity")
    public AjaxResult updateMaterialValidity(@PathVariable Long materialId,
            @RequestBody ExpertMaterial material)
    {
        return success(workspaceService.updateMaterialValidity(materialId, material,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @DeleteMapping("/materials/{materialId}/files/{fileId}")
    public AjaxResult deleteMaterialFile(@PathVariable Long materialId, @PathVariable Long fileId)
    {
        workspaceService.deleteMaterialFile(materialId, fileId, SecurityUtils.getUserId(),
                SecurityUtils.getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:material:add')")
    @PostMapping("/materials/{materialId}/submit")
    public AjaxResult submitMaterialRevision(@PathVariable Long materialId)
    {
        return success(workspaceService.submitMaterialRevision(materialId,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:material:retry')")
    @PostMapping("/materials/retry")
    public AjaxResult retryMaterial(@RequestParam Long materialId)
    {
        workspaceService.retryMaterial(materialId, SecurityUtils.getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/materials/{materialId}/preview")
    public ResponseEntity<byte[]> previewMaterial(@PathVariable Long materialId,
            @RequestParam(defaultValue = "raw") String mode)
    {
        ExpertMaterialPreviewService.PreviewFile preview = previewService.preview(materialId,
                SecurityUtils.getUserId(), true, "text".equalsIgnoreCase(mode));
        return ResponseEntity.ok()
                .header("Content-Type", preview.getContentType())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(preview.getBytes());
    }

    @PreAuthorize("@ss.hasPermi('expert:material:list')")
    @GetMapping("/materials/{materialId}/files/{fileId}/preview")
    public ResponseEntity<byte[]> previewMaterialFile(@PathVariable Long materialId,
            @PathVariable Long fileId, @RequestParam(defaultValue = "raw") String mode)
    {
        ExpertMaterialPreviewService.PreviewFile preview = previewService.previewFile(materialId,
                fileId, SecurityUtils.getUserId(), true, "text".equalsIgnoreCase(mode));
        return ResponseEntity.ok()
                .header("Content-Type", preview.getContentType())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(preview.getBytes());
    }

    @PreAuthorize("@ss.hasPermi('expert:graph:view')")
    @GetMapping("/graph")
    public AjaxResult graph()
    {
        return success(workspaceService.graph(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:graph:view')")
    @GetMapping("/profile/versions")
    public AjaxResult profileVersions()
    {
        return success(versionService.timeline(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:graph:view')")
    @GetMapping("/profile/versions/{releaseId}")
    public AjaxResult profileVersion(@PathVariable Long releaseId)
    {
        return success(versionService.detail(SecurityUtils.getUserId(), releaseId));
    }

    @PreAuthorize("@ss.hasPermi('expert:workspace:view')")
    @GetMapping("/profile-reviews")
    public AjaxResult profileReviews()
    {
        return success(profileUpdateService.approvedForExpert(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:workspace:view')")
    @GetMapping("/risk-alerts")
    public AjaxResult riskAlerts()
    {
        return success(riskReviewService.acknowledgedForExpert(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:status:view')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(workspaceService.statusSnapshot(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:status:view')")
    @PostMapping("/status")
    public AjaxResult changeStatus(@Validated @RequestBody ExpertStatusChange change)
    {
        return success(workspaceService.submitStatusChange(change, SecurityUtils.getUserId(),
                SecurityUtils.getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:status:view')")
    @PostMapping("/status/requests/{requestId}/cancel")
    public AjaxResult cancelStatusRequest(@PathVariable Long requestId,
            @RequestParam(required = false) Integer version)
    {
        return success(workspaceService.cancelStatusRequest(requestId, version,
                SecurityUtils.getUserId(), SecurityUtils.getUsername()));
    }

    static ResponseEntity<byte[]> wordTemplateResponse(byte[] bytes)
    {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("专家信息导入模板.docx", java.nio.charset.StandardCharsets.UTF_8)
                        .build().toString())
                .header("Cache-Control", "no-store")
                .body(bytes);
    }
}
