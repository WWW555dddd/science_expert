package com.ruoyi.expert.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.expert.domain.ExpertRiskEventRequest;
import com.ruoyi.expert.domain.ExpertRiskReview;
import com.ruoyi.expert.domain.ExpertRiskReviewAction;
import com.ruoyi.expert.domain.ExpertServiceEvidence;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.ExpertRiskReviewMapper;

/**
 * 投诉、低评价和信用预警的审核。
 */
@Service
public class ExpertRiskReviewService
{
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> RISK_TYPES = Set.of(
            "COMPLAINT", "LOW_EVALUATION", "CREDIT_WARNING");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");
    private static final List<String> NEGATIVE_TERMS = List.of(
            "投诉", "不满意", "逾期", "延期", "违规", "失职", "信用风险",
            "负面", "差评", "争议");

    @Autowired
    private ExpertRiskReviewMapper riskMapper;

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private ExpertCreditService creditService;

    @Transactional
    public ExpertRiskReview createFromEvidence(ExpertServiceEvidence evidence)
    {
        String content = String.join(" ", safe(evidence.getTitle()),
                safe(evidence.getSummary()), safe(evidence.getRawPayload()));
        String riskType = detectRiskType(evidence.getEvidenceType(), content);
        if (riskType == null)
        {
            return null;
        }
        ExpertRiskReview existing = riskMapper.selectBySource(
                evidence.getSourceEventId(), riskType);
        if (existing != null)
        {
            return existing;
        }
        ExpertRiskReview risk = baseRisk(evidence.getUserId(), riskType,
                evidence.getSourceEventId(), evidence.getTitle(), content,
                evidence.getOccurredAt(), evidence.getSourceSystem(),
                evidence.getSourceKind());
        risk.setEvidenceId(evidence.getEvidenceId());
        riskMapper.insertRisk(risk);
        return risk;
    }

    @Transactional
    public ExpertRiskReview createEvent(ExpertRiskEventRequest request)
    {
        ExpertRiskReview existing = riskMapper.selectBySource(
                request.getSourceEventId(), upper(request.getRiskType()));
        if (existing != null)
        {
            return existing;
        }
        ExpertRiskReview risk = baseRisk(request.getUserId(), request.getRiskType(),
                request.getSourceEventId(), request.getTitle(), request.getSummary(),
                request.getOccurredAt(), request.getSourceSystem(),
                StringUtils.isEmpty(request.getSourceKind())
                        ? "EXTERNAL" : request.getSourceKind());
        risk.setSeverity(upper(request.getSeverity()));
        validateRisk(risk);
        riskMapper.insertRisk(risk);
        return riskMapper.selectById(risk.getRiskId());
    }

    @Transactional(readOnly = true)
    public List<ExpertRiskReview> list(String status)
    {
        return riskMapper.selectList(StringUtils.isEmpty(status)
                ? null : upper(status));
    }

    @Transactional(readOnly = true)
    public ExpertRiskReview detail(Long riskId)
    {
        ExpertRiskReview risk = riskMapper.selectById(riskId);
        if (risk == null)
        {
            throw new ServiceException("风险预警不存在");
        }
        return risk;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> acknowledgedForExpert(Long userId)
    {
        if (userId == null)
        {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExpertRiskReview risk : riskMapper.selectAcknowledgedByUserId(userId, 20))
        {
            Map<String, Object> alert = new LinkedHashMap<>();
            alert.put("riskId", risk.getRiskId());
            alert.put("riskType", risk.getRiskType());
            alert.put("severity", risk.getSeverity());
            alert.put("title", risk.getTitle());
            alert.put("summary", risk.getSummary());
            alert.put("reviewerName", risk.getReviewerName());
            alert.put("reviewComment", risk.getReviewComment());
            alert.put("occurredAt", risk.getOccurredAt());
            alert.put("resolvedTime", risk.getResolvedTime());
            result.add(alert);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> review(Long riskId, ExpertRiskReviewAction action,
            LoginUser reviewer)
    {
        String operation = upper(action.getAction());
        if (!"ACKNOWLEDGE".equals(operation) && !"DISMISS".equals(operation))
        {
            throw new ServiceException("审核动作仅支持 ACKNOWLEDGE 或 DISMISS");
        }
        if ("DISMISS".equals(operation) && StringUtils.isEmpty(action.getComment()))
        {
            throw new ServiceException("忽略风险预警时必须填写审核意见");
        }
        ExpertRiskReview risk = riskMapper.selectByIdForUpdate(riskId);
        if (risk == null || !"OPEN".equals(risk.getStatus()))
        {
            throw new ServiceException("风险预警已处理或不存在");
        }
        if (!Objects.equals(risk.getVersion(), action.getVersion()))
        {
            throw new ServiceException("风险预警已被处理，请刷新后重试");
        }
        String status = "ACKNOWLEDGE".equals(operation)
                ? "ACKNOWLEDGED" : "DISMISSED";
        if (riskMapper.resolveRisk(riskId, status, action.getVersion(),
                reviewer.getUserId(), reviewer.getUsername(), action.getComment()) == 0)
        {
            throw new ServiceException("风险预警已被处理，请刷新后重试");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("risk", detail(riskId));
        if ("ACKNOWLEDGED".equals(status))
        {
            result.put("creditChange", creditService.deductForRisk(risk, reviewer));
        }
        return result;
    }

    private ExpertRiskReview baseRisk(Long userId, String riskType,
            String sourceEventId, String title, String summary, String occurredAt,
            String sourceSystem, String sourceKind)
    {
        if (platformMapper.selectProfileByUserId(userId) == null)
        {
            throw new ServiceException("专家档案不存在");
        }
        ExpertRiskReview risk = new ExpertRiskReview();
        risk.setUserId(userId);
        risk.setRiskType(upper(riskType));
        risk.setSeverity("MEDIUM");
        risk.setSourceEventId(sourceEventId);
        risk.setTitle(StringUtils.isEmpty(title) ? "专家风险预警" : title);
        risk.setSummary(summary);
        risk.setStatus("OPEN");
        risk.setSourceSystem(StringUtils.isEmpty(sourceSystem)
                ? "external-risk" : sourceSystem);
        risk.setSourceKind(StringUtils.isEmpty(sourceKind)
                ? "EXTERNAL" : upper(sourceKind));
        risk.setOccurredAt(normalizeDateTime(occurredAt));
        validateRisk(risk);
        return risk;
    }

    private static String detectRiskType(String evidenceType, String content)
    {
        String type = upper(evidenceType);
        String text = safe(content).toLowerCase(Locale.ROOT);
        if ("EVALUATION".equals(type) && containsNegative(text))
        {
            return "LOW_EVALUATION";
        }
        if ("CREDIT".equals(type) && containsNegative(text))
        {
            return "CREDIT_WARNING";
        }
        if (text.contains("投诉"))
        {
            return "COMPLAINT";
        }
        return null;
    }

    private static boolean containsNegative(String content)
    {
        return NEGATIVE_TERMS.stream().anyMatch(content::contains);
    }

    private static void validateRisk(ExpertRiskReview risk)
    {
        if (!RISK_TYPES.contains(risk.getRiskType()))
        {
            throw new ServiceException("风险类型仅支持 COMPLAINT、LOW_EVALUATION、CREDIT_WARNING");
        }
        if (!SEVERITIES.contains(risk.getSeverity()))
        {
            throw new ServiceException("风险等级仅支持 LOW、MEDIUM、HIGH");
        }
    }

    private static String normalizeDateTime(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return LocalDateTime.now().format(DATETIME_FORMAT);
        }
        String normalized = value.trim().replace('T', ' ');
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm"))
        {
            try
            {
                return LocalDateTime.parse(normalized,
                        DateTimeFormatter.ofPattern(pattern)).format(DATETIME_FORMAT);
            }
            catch (DateTimeParseException ignored)
            {
                // 尝试下一种允许的输入格式。
            }
        }
        throw new ServiceException("发生时间格式不正确");
    }

    private static String upper(String value)
    {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
