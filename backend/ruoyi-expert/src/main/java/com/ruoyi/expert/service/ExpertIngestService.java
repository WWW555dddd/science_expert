package com.ruoyi.expert.service;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.expert.config.ExpertOcrProperties;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;

/**
 * 资料入库与模型辅助预审适配层。
 *
 * 使用 Redis List 作为消息队列，优先调用远程 DeepSeek 模型，
 * 远程服务不可用时降级到本地基本信息提取。证据不足时不生成画像分数。
 */
@Service
public class ExpertIngestService
{
    private static final Logger log = LoggerFactory.getLogger(ExpertIngestService.class);
    public static final String QUEUE_KEY = "expert:ingest:queue";

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private DeepSeekAgentService deepSeekAgent;

    @Autowired
    private ExpertObjectStorage objectStorage;

    @Autowired
    private AliyunOcrService ocrService;

    @Autowired
    private ExpertOcrProperties ocrProperties;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ExpertIngestPersistenceService persistenceService;

    @Autowired
    private KnowledgeGraphFactExtractionService graphFactExtractionService;

    public void enqueue(Long materialId)
    {
        if (materialId == null)
        {
            return;
        }
        if (redisTemplate != null)
        {
            try
            {
                redisTemplate.opsForList().leftPush(QUEUE_KEY, String.valueOf(materialId));
                return;
            }
            catch (Exception ex)
            {
                log.warn("Redis 队列不可用，将使用本地异步处理: {}", ex.getMessage());
            }
        }
        CompletableFuture.runAsync(() -> processMaterial(materialId));
    }

    public void enqueueAfterCommit(Long materialId)
    {
        if (materialId == null)
        {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    enqueue(materialId);
                }
            });
            return;
        }
        enqueue(materialId);
    }

    @Scheduled(fixedDelay = 3000L)
    public void pollQueue()
    {
        platformMapper.releaseStaleMaterialJobs();
        if (redisTemplate == null)
        {
            return;
        }
        for (int i = 0; i < 5; i++)
        {
            String message;
            try
            {
                message = redisTemplate.opsForList().rightPop(QUEUE_KEY);
            }
            catch (Exception ex)
            {
                return;
            }
            if (StringUtils.isEmpty(message))
            {
                return;
            }
            try
            {
                processMaterial(Long.valueOf(message));
            }
            catch (Exception ex)
            {
                log.error("专家资料处理失败: {}", message, ex);
            }
        }
    }

    @Scheduled(fixedDelay = 30000L)
    public void recoverQueuedMaterials()
    {
        for (Long materialId : platformMapper.selectRecoverableQueuedMaterialIds(20))
        {
            enqueue(materialId);
        }
    }

    public void processMaterial(Long materialId)
    {
        String processingToken = UUID.randomUUID().toString();
        if (platformMapper.claimMaterialJob(materialId, processingToken) == 0)
        {
            return;
        }
        ExpertMaterial material = platformMapper.selectMaterialById(materialId);
        if (material == null)
        {
            return;
        }
        boolean adminApproved = "APPROVED".equals(material.getReviewStatus());
        String processingReviewStatus = adminApproved ? "APPROVED" : "PENDING";
        ExpertProfile profile = platformMapper.selectProfileByUserId(material.getUserId());
        if (profile == null)
        {
            update(materialId, processingToken, "FAILED", processingReviewStatus, 0, 1,
                    "未找到专家档案，无法执行资料提取");
            return;
        }
        try
        {
            update(materialId, processingToken, "EXTRACTING", processingReviewStatus, 0, 0,
                    deepSeekAgent.isConfigured() ? "正在调用DeepSeek分析文件" : "远程模型未配置，使用本地规则提取");
            update(materialId, processingToken, "MASKING", processingReviewStatus, 0, 0,
                    "正在执行敏感信息脱敏");
            String documentText = readMaterialDocumentText(material);
            boolean documentTextEmpty = StringUtils.isEmpty(documentText);
            JSONObject remoteAnalysis = null;
            String remoteError = null;
            try
            {
                remoteAnalysis = deepSeekAgent.analyze(profile, material, documentText);
            }
            catch (Exception ex)
            {
                remoteError = ex.getMessage();
                log.warn("DeepSeek模型调用失败，回退本地规则: {}", remoteError);
            }
            JSONObject graphFacts = graphFactExtractionService.resolveFacts(
                    remoteAnalysis, profile, documentText);
            String graphFactSource = remoteAnalysis != null
                    && remoteAnalysis.getJSONObject("graphFacts") != null ? "LLM" : "RULE";
            List<ExpertIngestPersistenceService.FieldMappingDraft> mappings =
                    remoteAnalysis == null
                            ? buildFieldMappings(profile, documentText)
                            : buildRemoteFieldMappings(remoteAnalysis);
            int mappingCount = mappings.size();
            StringBuilder issues = new StringBuilder();
            int issueCount = 0;
            issueCount += issue(issues, documentTextEmpty,
                    "未从材料中提取到可读文本，扫描件需确认OCR服务或人工核对原件");
            if (remoteAnalysis != null)
            {
                issueCount += appendRemoteIssues(issues, remoteAnalysis);
            }
            if (remoteError != null)
            {
                issueCount += issue(issues, true, "DeepSeek调用失败，已回退本地规则：" + remoteError);
            }
            int expectedDimensions = platformMapper.selectGraphDimensions(profile.getUserId(), "DRAFT").size();
            int scoredDimensions = remoteAnalysis == null
                    || remoteAnalysis.getJSONObject("scores") == null
                    ? 0 : remoteAnalysis.getJSONObject("scores").size();
            issueCount += issue(issues, scoredDimensions < expectedDimensions,
                    "画像证据不足：仅生成 " + scoredDimensions + "/" + expectedDimensions
                            + " 个维度，缺失维度标记为 INSUFFICIENT_EVIDENCE");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getOrganization()), "缺少所属单位");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getProfessionalTitle()), "缺少职称职务");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getResearchField()), "缺少专业方向");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getIndustryField()), "缺少产业领域");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getPhone()), "缺少联系电话");
            issueCount += issue(issues, StringUtils.isEmpty(profile.getEmail()), "缺少电子邮箱");
            update(materialId, processingToken, "MAPPING", processingReviewStatus, mappingCount,
                    issueCount,
                    "已生成 " + mappingCount + " 条基本信息提取结果");
            update(materialId, processingToken, "PRE_REVIEWING", processingReviewStatus,
                    mappingCount, issueCount,
                    "正在校验缺项、冲突和材料完整性");
            String provider = remoteAnalysis != null ? "DeepSeek远程分析" : "本地规则";
            String message = provider + "初审完成。"
                    + (issueCount == 0 ? "未发现明显缺项" : "发现 " + issueCount + " 个问题：" + issues)
                    + (adminApproved ? " 管理员直审材料，无需人工审核。" : "");
            boolean persisted = persistenceService.persist(
                    new ExpertIngestPersistenceService.PersistCommand(
                            materialId, processingToken, material.getUserId(),
                            adminApproved ? "REVIEWED" : "REVIEW_PENDING",
                            processingReviewStatus, mappingCount, issueCount,
                            limitMessage(message), adminApproved, mappings, remoteAnalysis,
                            graphFacts, graphFactSource));
            if (!persisted)
            {
                log.info("忽略已过期的资料处理结果，materialId={}", materialId);
            }
        }
        catch (Exception ex)
        {
            log.error("资料处理异常", ex);
            update(materialId, processingToken, "FAILED", processingReviewStatus, 0, 1,
                    "处理失败：" + ex.getMessage());
        }
    }

    private List<ExpertIngestPersistenceService.FieldMappingDraft> buildFieldMappings(
            ExpertProfile profile, String documentText)
    {
        List<ExpertIngestPersistenceService.FieldMappingDraft> mappings = new ArrayList<>();
        mapping(mappings, "real_name", "姓名",
                documentValue(documentText, "姓名", profile.getRealName()),
                documentText.isEmpty() ? 0.82 : 0.96, evidence(documentText));
        mapping(mappings, "organization", "工作单位",
                documentValue(documentText, "工作单位", profile.getOrganization()),
                documentText.isEmpty() ? 0.80 : 0.93, evidence(documentText));
        mapping(mappings, "professional_title", "职称",
                documentValue(documentText, "职称", profile.getProfessionalTitle()),
                documentText.isEmpty() ? 0.78 : 0.91, evidence(documentText));
        mapping(mappings, "research_field", "研究领域",
                documentValue(documentText, "研究领域", profile.getResearchField()),
                documentText.isEmpty() ? 0.76 : 0.89, evidence(documentText));
        mapping(mappings, "industry_field", "产业方向",
                documentValue(documentText, "产业方向", profile.getIndustryField()),
                documentText.isEmpty() ? 0.74 : 0.88, evidence(documentText));
        mapping(mappings, "service_region", "服务区域",
                documentValue(documentText, "服务区域", profile.getServiceRegion()),
                documentText.isEmpty() ? 0.74 : 0.86, evidence(documentText));
        mapping(mappings, "phone", "联系电话",
                maskPhone(documentValue(documentText, "联系电话", profile.getPhone())),
                documentText.isEmpty() ? 0.82 : 0.95, evidence(documentText));
        mapping(mappings, "email", "邮箱",
                maskEmail(documentValue(documentText, "电子邮箱", profile.getEmail())),
                documentText.isEmpty() ? 0.82 : 0.94, evidence(documentText));
        return mappings;
    }

    private List<ExpertIngestPersistenceService.FieldMappingDraft> buildRemoteFieldMappings(
            JSONObject analysis)
    {
        JSONArray fields = analysis.getJSONArray("fields");
        if (fields == null)
        {
            return List.of();
        }
        List<ExpertIngestPersistenceService.FieldMappingDraft> mappings = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++)
        {
            JSONObject field = fields.getJSONObject(i);
            String dbField = field.getString("dbField");
            String value = field.getString("value");
            if (!isAllowedField(dbField) || StringUtils.isEmpty(value))
            {
                continue;
            }
            if ("phone".equals(dbField))
            {
                value = maskPhone(value);
            }
            else if ("email".equals(dbField))
            {
                value = maskEmail(value);
            }
            double confidence = clamp(field.getDoubleValue("confidence"), 0.0, 1.0);
            mappings.add(new ExpertIngestPersistenceService.FieldMappingDraft(
                    dbField, field.getString("fileField"), value, confidence,
                    field.getString("evidence"), field.getBooleanValue("conflict") ? "1" : "0"));
        }
        return mappings;
    }

    private int appendRemoteIssues(StringBuilder issues, JSONObject analysis)
    {
        JSONArray remoteIssues = analysis.getJSONArray("issues");
        if (remoteIssues == null)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < remoteIssues.size(); i++)
        {
            String value = remoteIssues.getString(i);
            if (StringUtils.isNotEmpty(value))
            {
                if (issues.length() > 0)
                {
                    issues.append("；");
                }
                issues.append(value);
                count++;
            }
        }
        return count;
    }

    private void mapping(List<ExpertIngestPersistenceService.FieldMappingDraft> mappings,
            String dbField, String fileField, String value, double confidence, String evidence)
    {
        if (StringUtils.isNotEmpty(value))
        {
            mappings.add(new ExpertIngestPersistenceService.FieldMappingDraft(
                    dbField, fileField, value, confidence, evidence, "0"));
        }
    }

    private void update(Long materialId, String processingToken, String jobStatus,
            String reviewStatus, int fields, int issues, String message)
    {
        platformMapper.updateMaterialJob(materialId, processingToken, jobStatus, reviewStatus,
                fields, issues, message);
    }

    private int issue(StringBuilder issues, boolean condition, String message)
    {
        if (condition)
        {
            if (issues.length() > 0)
            {
                issues.append("；");
            }
            issues.append(message);
            return 1;
        }
        return 0;
    }

    private static String maskPhone(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return value;
        }
        return value.length() >= 7 ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : "****";
    }

    private static String maskEmail(String value)
    {
        if (StringUtils.isEmpty(value) || !value.contains("@"))
        {
            return value;
        }
        int at = value.indexOf('@');
        return (at <= 1 ? "*" : value.substring(0, 2) + "***") + value.substring(at);
    }

    /**
     * 读取同一材料版本内全部文件正文。
     */
    private String readMaterialDocumentText(ExpertMaterial material)
    {
        List<ExpertMaterialFile> files = platformMapper.selectMaterialFiles(material.getMaterialId());
        if (files.isEmpty())
        {
            return readDocumentText(material);
        }
        StringBuilder combined = new StringBuilder();
        for (ExpertMaterialFile file : files)
        {
            ExpertMaterial fileMaterial = new ExpertMaterial();
            fileMaterial.setMaterialId(material.getMaterialId());
            fileMaterial.setMaterialCategory(material.getMaterialCategory());
            fileMaterial.setFileName(file.getFileName());
            fileMaterial.setFilePath(file.getFilePath());
            fileMaterial.setFileType(file.getFileType());
            String text = readDocumentText(fileMaterial);
            if (StringUtils.isNotEmpty(text))
            {
                combined.append("【文件：").append(file.getFileName()).append("】\n")
                        .append(text).append("\n\n");
            }
        }
        return limitText(combined.toString());
    }

    /**
     * 读取单个文件正文：DOCX/文本直接读取，PDF优先提取文本，扫描PDF和图片调用OCR。
     */
    private String readDocumentText(ExpertMaterial material)
    {
        if (StringUtils.isEmpty(material.getFilePath()))
        {
            return "";
        }
        if (material.getFilePath().startsWith("minio://"))
        {
            try
            {
                return extractDocumentText(material, objectStorage.download(material.getFilePath()));
            }
            catch (Exception ex)
            {
                log.warn("读取 MinIO 专家材料失败: {}", ex.getMessage());
                return "";
            }
        }
        String relative = material.getFilePath();
        if (relative.startsWith("/profile"))
        {
            relative = relative.substring("/profile".length());
        }
        Path profileRoot = Path.of(RuoYiConfig.getProfile()).toAbsolutePath().normalize();
        Path file = profileRoot.resolve(relative.replaceFirst("^[/\\\\]+", "")).normalize();
        if (!file.startsWith(profileRoot) || !Files.isRegularFile(file))
        {
            return "";
        }
        try
        {
            return extractDocumentText(material, Files.readAllBytes(file));
        }
        catch (Exception ex)
        {
            log.warn("读取专家材料文本失败: {}", ex.getMessage());
        }
        return "";
    }

    private String extractDocumentText(ExpertMaterial material, byte[] content) throws Exception
    {
        String type = material.getFileType() == null ? "" : material.getFileType().toLowerCase();
        if ("docx".equals(type))
        {
            try (InputStream input = new ByteArrayInputStream(content))
            {
                return readDocx(input);
            }
        }
        if ("txt".equals(type) || "md".equals(type) || "csv".equals(type))
        {
            return limitText(new String(content, StandardCharsets.UTF_8));
        }
        if ("pdf".equals(type))
        {
            return extractPdfText(content, material.getMaterialCategory());
        }
        if ("png".equals(type) || "jpg".equals(type) || "jpeg".equals(type))
        {
            return limitText(ocrService.recognize(content, type, material.getMaterialCategory()));
        }
        return "";
    }

    private String extractPdfText(byte[] content, String materialCategory) throws Exception
    {
        try (PDDocument document = Loader.loadPDF(content))
        {
            String text = new PDFTextStripper().getText(document).trim();
            if (text.length() >= 30)
            {
                return limitText(text);
            }
            if (!ocrService.isAvailable())
            {
                return limitText(text);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder ocrText = new StringBuilder(text);
            int pages = Math.min(document.getNumberOfPages(), Math.max(1, ocrProperties.getMaxPages()));
            for (int page = 0; page < pages; page++)
            {
                BufferedImage image = renderer.renderImageWithDPI(page, 200);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                String pageText = ocrService.recognize(output.toByteArray(), "png",
                        materialCategory);
                if (StringUtils.isNotEmpty(pageText))
                {
                    ocrText.append('\n').append(pageText);
                }
            }
            return limitText(ocrText.toString());
        }
    }

    private String readDocx(InputStream input) throws Exception
    {
        StringBuilder text = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(input))
        {
            for (XWPFParagraph paragraph : document.getParagraphs())
            {
                text.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables())
            {
                for (XWPFTableRow row : table.getRows())
                {
                    for (XWPFTableCell cell : row.getTableCells())
                    {
                        text.append(cell.getText()).append('\t');
                    }
                    text.append('\n');
                }
            }
        }
        return limitText(text.toString());
    }

    private static String limitText(String text)
    {
        if (text == null)
        {
            return "";
        }
        String normalized = text.replace('\u0000', ' ').trim();
        return normalized.length() > 50000 ? normalized.substring(0, 50000) : normalized;
    }

    private static String limitMessage(String message)
    {
        if (message == null)
        {
            return "";
        }
        return message.length() > 950 ? message.substring(0, 950) : message;
    }

    private static String documentValue(String text, String label, String fallback)
    {
        if (StringUtils.isEmpty(text))
        {
            return fallback;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(label) + "\\s*[:：]?\\s*([^\\r\\n\\t；;]{1,100})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find())
        {
            String value = matcher.group(1).trim();
            if (StringUtils.isNotEmpty(value))
            {
                return value;
            }
        }
        return fallback;
    }

    private static String evidence(String text)
    {
        return StringUtils.isEmpty(text) ? "专家档案表单（材料无可解析正文）" : "上传文档正文基本信息提取";
    }

    private static boolean isAllowedField(String dbField)
    {
        return "real_name".equals(dbField) || "organization".equals(dbField)
                || "professional_title".equals(dbField) || "research_field".equals(dbField)
                || "industry_field".equals(dbField) || "service_region".equals(dbField)
                || "phone".equals(dbField) || "email".equals(dbField);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
