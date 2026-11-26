package com.ruoyi.expert.controller;

import java.util.HashMap;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.expert.domain.ExpertAdminSaveRequest;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.KnowledgeGraphAgentRunRequest;
import com.ruoyi.expert.service.ExpertAdminService;
import com.ruoyi.expert.service.ExpertObjectStorage;
import com.ruoyi.expert.service.ExpertReviewService;
import com.ruoyi.expert.service.KnowledgeGraphAgentRuntimeService;
import com.ruoyi.expert.service.KnowledgeGraphProjectionService;
import com.ruoyi.expert.service.KnowledgeGraphQueryService;
import com.ruoyi.expert.service.KnowledgeGraphOutboxWorker;
import com.ruoyi.expert.service.KnowledgeGraphRebuildService;
import com.ruoyi.expert.service.ExpertProfileWordImportService;

/**
 * 管理控制台业务摘要。
 */
@RestController
@RequestMapping("/expert/admin")
public class ExpertAdminController extends BaseController
{
    private static final Set<String> ALLOWED_MATERIAL_EXTENSIONS =
            Set.of("pdf", "docx", "txt", "md", "csv", "png", "jpg", "jpeg");

    @Autowired
    private ExpertReviewService reviewService;

    @Autowired
    private ExpertAdminService adminService;

    @Autowired
    private ExpertObjectStorage objectStorage;

    @Autowired
    private KnowledgeGraphQueryService graphQueryService;

    @Autowired
    private KnowledgeGraphProjectionService graphProjectionService;

    @Autowired
    private KnowledgeGraphOutboxWorker graphOutboxWorker;

    @Autowired
    private KnowledgeGraphAgentRuntimeService graphAgentRuntimeService;

    @Autowired
    private KnowledgeGraphRebuildService graphRebuildService;

    @Autowired
    private ExpertProfileWordImportService profileWordImportService;

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/summary")
    public AjaxResult summary()
    {
        return success(reviewService.adminSummary());
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/jobs")
    public TableDataInfo jobs(ExpertMaterial query)
    {
        startPage();
        Map<String, Object> params = new HashMap<>();
        params.put("jobStatus", query.getJobStatus());
        return getDataTable(reviewService.jobs(params));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:list')")
    @GetMapping("/experts")
    public TableDataInfo experts(String userName, String realName, String organization,
            String researchField, String industryField, String phone,
            String reviewStatus, String serviceStatus)
    {
        startPage();
        Map<String, Object> query = new HashMap<>();
        query.put("userName", userName);
        query.put("realName", realName);
        query.put("organization", organization);
        query.put("researchField", researchField);
        query.put("industryField", industryField);
        query.put("phone", phone);
        query.put("reviewStatus", reviewStatus);
        query.put("serviceStatus", serviceStatus);
        return getDataTable(adminService.list(query));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @GetMapping("/experts/users")
    public AjaxResult expertUsers()
    {
        List<Map<String, Object>> users = adminService.expertUsers();
        return success(users);
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @GetMapping("/experts/profile-import-template")
    public ResponseEntity<byte[]> downloadProfileImportTemplate()
    {
        return ExpertWorkspaceController.wordTemplateResponse(
                profileWordImportService.createTemplate());
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping(value = "/experts/profile-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult importProfile(@RequestParam("file") MultipartFile file)
    {
        return success(profileWordImportService.importProfile(file));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @GetMapping("/experts/{userId}/certifications")
    public AjaxResult certifications(@PathVariable Long userId)
    {
        return success(adminService.certifications(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @GetMapping("/experts/{userId}/schedules")
    public AjaxResult schedules(@PathVariable Long userId)
    {
        return success(adminService.schedules(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PutMapping("/experts/{userId}/schedules")
    public AjaxResult saveSchedules(@PathVariable Long userId,
            @RequestBody List<ExpertSchedule> schedules)
    {
        adminService.saveSchedules(userId, schedules, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @GetMapping("/experts/{userId}/unavailable-periods")
    public AjaxResult unavailablePeriods(@PathVariable Long userId)
    {
        return success(adminService.unavailablePeriods(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:list')")
    @GetMapping("/experts/{userId}/profile-versions")
    public AjaxResult profileVersions(@PathVariable Long userId)
    {
        return success(adminService.profileVersions(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:list')")
    @GetMapping("/experts/{userId}/profile-versions/{releaseId}")
    public AjaxResult profileVersion(@PathVariable Long userId, @PathVariable Long releaseId)
    {
        return success(adminService.profileVersion(userId, releaseId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:list')")
    @GetMapping("/experts/{userId}/graph")
    public AjaxResult graph(@PathVariable Long userId)
    {
        return success(adminService.graph(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/knowledge-graph/ontology")
    public AjaxResult knowledgeGraphOntology()
    {
        return success(graphQueryService.ontology());
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/knowledge-graph/overview")
    public AjaxResult knowledgeGraphOverview(
            @RequestParam(defaultValue = "400") int entityLimit,
            @RequestParam(defaultValue = "800") int relationLimit)
    {
        return success(graphQueryService.overviewSubgraph(entityLimit, relationLimit));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/knowledge-graph/experts/{userId}/subgraph")
    public AjaxResult knowledgeGraphSubgraph(@PathVariable Long userId,
            @RequestParam(defaultValue = "2") int depth)
    {
        adminService.ensureExpertUser(userId);
        return success(graphQueryService.expertSubgraph(userId, depth));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping("/knowledge-graph/experts/{userId}/project")
    public AjaxResult projectKnowledgeGraph(@PathVariable Long userId)
    {
        adminService.ensureExpertUser(userId);
        return success(graphProjectionService.projectExpert(userId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping("/knowledge-graph/outbox/run")
    public AjaxResult runKnowledgeGraphOutbox()
    {
        graphOutboxWorker.syncPendingEvents();
        return success();
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping("/knowledge-graph/rebuild")
    public AjaxResult rebuildKnowledgeGraph(
            @RequestParam(defaultValue = "false") boolean confirm)
    {
        if (!confirm)
        {
            return error("完整重建会清空 Neo4j 只读投影，请传 confirm=true 后重试");
        }
        return success(graphRebuildService.rebuildAll());
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping("/agent/runs")
    public AjaxResult runKnowledgeGraphAgent(
            @Validated @RequestBody KnowledgeGraphAgentRunRequest request)
    {
        return success(graphAgentRuntimeService.run(request, getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:view')")
    @GetMapping("/agent/runs/{runId}")
    public AjaxResult knowledgeGraphAgentRun(@PathVariable String runId)
    {
        return success(graphAgentRuntimeService.getRun(runId));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PostMapping(value = "/experts/{userId}/materials/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AjaxResult uploadExpertMaterial(@PathVariable Long userId,
            @RequestParam("file") MultipartFile file)
    {
        adminService.ensureExpertUser(userId);
        if (file == null || file.isEmpty())
        {
            return error("请选择需要上传的认证材料");
        }
        if (file.getSize() > 10L * 1024 * 1024)
        {
            return error("单个文件不能超过10MB");
        }
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1)
                        .toLowerCase(Locale.ROOT)
                : "";
        if (!ALLOWED_MATERIAL_EXTENSIONS.contains(extension))
        {
            return error("不支持该文件类型，请上传PDF、DOCX、TXT、CSV、MD或图片");
        }
        try
        {
            String filePath = objectStorage.upload(userId, originalFilename, file.getContentType(),
                    file.getSize(), file.getInputStream());
            AjaxResult result = success();
            result.put("filePath", filePath);
            result.put("fileName", originalFilename);
            result.put("fileSize", file.getSize());
            result.put("fileType", extension);
            result.put("contentType", file.getContentType());
            return result;
        }
        catch (Exception ex)
        {
            return error(ex.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:edit')")
    @PutMapping("/experts/{userId}")
    public AjaxResult saveExpert(@PathVariable Long userId,
            @Validated @RequestBody ExpertAdminSaveRequest request)
    {
        return success(adminService.save(request, userId, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('expert:admin:expert:remove')")
    @DeleteMapping("/experts/{userId}")
    public AjaxResult deleteExpert(@PathVariable Long userId)
    {
        adminService.delete(userId, getUserId(), getUsername());
        return success();
    }
}
