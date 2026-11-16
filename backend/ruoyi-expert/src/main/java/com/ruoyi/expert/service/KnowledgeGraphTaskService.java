package com.ruoyi.expert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 将一次专家匹配需求投影为任务图谱节点。
 */
@Service
public class KnowledgeGraphTaskService
{
    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private KnowledgeGraphOntologyService ontologyService;

    @Autowired
    private KnowledgeGraphOutboxService outboxService;

    @Transactional
    public Map<String, Object> createTask(String runId, String objective,
            String serviceDate, String serviceRegion, String serviceMode,
            String industry, String requiredCapabilities, List<String> suggestedTerms)
    {
        ontologyService.requireEntityType("Task");
        List<String> capabilities = resolveCapabilities(requiredCapabilities, industry,
                suggestedTerms);
        Map<String, Object> taskProperties = new LinkedHashMap<>();
        taskProperties.put("title", abbreviate(objective, 255));
        taskProperties.put("objective", abbreviateLong(objective));
        taskProperties.put("serviceDate", serviceDate);
        taskProperties.put("serviceRegion", serviceRegion);
        taskProperties.put("serviceMode", serviceMode);
        taskProperties.put("industry", industry);
        taskProperties.put("status", "OPEN");
        taskProperties.put("agentRunId", runId);

        Long taskId = upsertEntity("Task", "task:" + runId,
                abbreviate(objective, 255), taskProperties, "ACCEPTED");
        Map<String, String> canonicalCapabilities = new LinkedHashMap<>();
        for (Map<String, Object> mapping : graphMapper.selectCapabilityMappings())
        {
            canonicalCapabilities.put(
                    String.valueOf(mapping.get("capabilityName")).toLowerCase(Locale.ROOT),
                    String.valueOf(mapping.get("capabilityName")));
        }
        int relationCount = 0;
        Set<String> canonicalRequiredCapabilities = new LinkedHashSet<>();
        for (String capability : capabilities)
        {
            canonicalRequiredCapabilities.add(
                    canonicalCapability(capability, canonicalCapabilities));
        }
        for (String capability : canonicalRequiredCapabilities)
        {
            Map<String, Object> capabilityProperties = new LinkedHashMap<>();
            capabilityProperties.put("name", capability);
            capabilityProperties.put("requirementSource", "MATCH_REQUEST");
            Long capabilityId = upsertEntity("Capability",
                    "capability:" + sha256(normalize(capability)),
                    capability, capabilityProperties, "ACCEPTED");
            relationCount += link(taskId, capabilityId, runId, objective,
                    capability);
        }
        outboxService.enqueue("TASK_CREATED", "TASK", taskId,
                Map.of("taskEntityId", taskId, "runId", runId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskEntityId", taskId);
        result.put("runId", runId);
        result.put("requiredCapabilityCount", canonicalRequiredCapabilities.size());
        result.put("requiredCapabilities", new ArrayList<>(canonicalRequiredCapabilities));
        result.put("relationCount", relationCount);
        return result;
    }

    private static String canonicalCapability(String requested,
            Map<String, String> canonicalCapabilities)
    {
        String normalized = normalize(requested);
        for (Map.Entry<String, String> entry : canonicalCapabilities.entrySet())
        {
            String canonicalNormalized = normalize(entry.getKey());
            if (normalized.equals(canonicalNormalized)
                    || normalized.contains(canonicalNormalized)
                    || canonicalNormalized.contains(normalized))
            {
                return entry.getValue();
            }
        }
        return requested;
    }

    private int link(Long taskId, Long capabilityId, String runId, String objective,
            String capability)
    {
        ontologyService.requireRelationType("REQUIRES_CAPABILITY", "Task", "Capability");
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("sourceEntityId", taskId);
        relation.put("relationType", "REQUIRES_CAPABILITY");
        relation.put("targetEntityId", capabilityId);
        relation.put("confidence", 1.0);
        relation.put("reviewStatus", "ACCEPTED");
        relation.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        relation.put("sourceKind", "MANUAL_REQUEST");
        relation.put("propertiesJson", JSON.toJSONString(Map.of(
                "agentRunId", runId,
                "requirement", capability)));
        graphMapper.upsertRelation(relation);
        Long relationId = graphMapper.selectRelationId(
                taskId, "REQUIRES_CAPABILITY", capabilityId);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceKey", sha256(String.join("|", "TASK_REQUIREMENT",
                String.valueOf(relationId), runId, capability)));
        evidence.put("relationId", relationId);
        evidence.put("entityId", capabilityId);
        evidence.put("sourceType", "MANUAL");
        evidence.put("sourceRef", "agent-run:" + runId);
        evidence.put("materialId", null);
        evidence.put("pageNo", null);
        evidence.put("charStart", null);
        evidence.put("charEnd", null);
        evidence.put("quoteHash", sha256(objective + "|" + capability));
        evidence.put("excerpt", "专家匹配需求要求能力：" + capability);
        evidence.put("confidence", 1.0);
        evidence.put("extractorVersion", "task-projection-v1");
        graphMapper.upsertEvidence(evidence);
        return 1;
    }

    private Long upsertEntity(String entityType, String externalKey, String name,
            Map<String, Object> properties, String reviewStatus)
    {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityType", entityType);
        entity.put("externalKey", externalKey);
        entity.put("canonicalName", name);
        entity.put("normalizedName", normalize(name));
        entity.put("propertiesJson", JSON.toJSONString(properties));
        entity.put("reviewStatus", reviewStatus);
        entity.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        graphMapper.upsertEntity(entity);
        Long entityId = graphMapper.selectEntityIdByExternalKey(entityType, externalKey);
        if (entityId == null)
        {
            throw new ServiceException("任务图谱实体写入后无法读取：" + entityType);
        }
        return entityId;
    }

    private static List<String> resolveCapabilities(String requiredCapabilities,
            String industry, List<String> suggestedTerms)
    {
        Set<String> values = new LinkedHashSet<>();
        if (requiredCapabilities != null)
        {
            for (String value : requiredCapabilities.split("[,，;；、\\n]"))
            {
                add(values, value);
            }
        }
        if (suggestedTerms != null)
        {
            suggestedTerms.forEach(value -> add(values, value));
        }
        add(values, industry);
        return new ArrayList<>(values).subList(0, Math.min(12, values.size()));
    }

    private static void add(Set<String> values, String value)
    {
        if (value != null && !value.isBlank())
        {
            values.add(abbreviate(value.trim(), 128));
        }
    }

    private static String normalize(String value)
    {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）()【】\\[\\]]+", "");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private static String abbreviate(String value, int maxLength)
    {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String abbreviateLong(String value)
    {
        return abbreviate(value, 4000);
    }

    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
