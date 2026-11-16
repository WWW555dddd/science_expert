package com.ruoyi.expert.controller;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertStatusChange;
import com.ruoyi.expert.service.ExpertWorkspaceService;
import com.ruoyi.expert.service.ExpertMaterialPreviewService;

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

    @PreAuthorize("@ss.hasPermi('expert:graph:view')")
    @GetMapping("/graph")
    public AjaxResult graph()
    {
        return success(workspaceService.graph(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:status:view')")
    @GetMapping("/status")
    public AjaxResult status()
    {
        return success(workspaceService.statusSnapshot(SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('expert:status:change,review:action')")
    @PostMapping("/status")
    public AjaxResult changeStatus(@Validated @RequestBody ExpertStatusChange change)
    {
        workspaceService.changeServiceStatus(change, SecurityUtils.getUserId(), SecurityUtils.getUsername());
        return success();
    }
}
