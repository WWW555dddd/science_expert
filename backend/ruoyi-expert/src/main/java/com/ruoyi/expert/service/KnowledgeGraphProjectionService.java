package com.ruoyi.expert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.mapper.ExpertPlatformMapper;
import com.ruoyi.expert.mapper.KnowledgeGraphMapper;

/**
 * 将结构化专家档案投影为知识图谱基础实体。
 *
 * <p>当前只投影档案能够直接证明的身份、单位、能力方向、产业和区域关系。
 * 30 维旧画像和历史分数不会转换为图谱事实。</p>
 */
@Service
public class KnowledgeGraphProjectionService
{
    private static final String SOURCE_TYPE = "EXPERT_PROFILE";
    private static final String SOURCE_KIND = "PROFILE_PROJECTION";

    @Autowired
    private ExpertPlatformMapper platformMapper;

    @Autowired
    private KnowledgeGraphMapper graphMapper;

    @Autowired
    private KnowledgeGraphOntologyService ontologyService;

    @Autowired
    private KnowledgeGraphOutboxService outboxService;

    @Transactional
    public Map<String, Object> projectExpert(Long userId)
    {
        ExpertProfile profile = platformMapper.selectProfileByUserId(userId);
        if (profile == null)
        {
            throw new ServiceException("专家档案不存在，无法投影知识图谱");
        }
        if (graphMapper.selectOntologyVersion(KnowledgeGraphOntologyService.ONTOLOGY_VERSION) == null)
        {
            throw new ServiceException("知识图谱版本尚未注册，请先执行 backend/sql/init.sql");
        }

        String reviewStatus = "APPROVED".equals(profile.getReviewStatus())
                ? "ACCEPTED" : "PENDING";
        Map<String, Object> expertProperties = new LinkedHashMap<>();
        expertProperties.put("userId", userId);
        expertProperties.put("realName", profile.getRealName());
        expertProperties.put("reviewStatus", profile.getReviewStatus());
        expertProperties.put("serviceStatus", profile.getServiceStatus());
        expertProperties.put("graphVersion", profile.getGraphVersion());
        Long expertId = upsertEntity("Expert", "expert:" + userId,
                requireName(profile.getRealName(), "未命名专家"), expertProperties, reviewStatus);
        if (profile.getUserName() != null && !profile.getUserName().isBlank())
        {
            graphMapper.upsertEntityAlias(expertId, profile.getUserName(),
                    normalize(profile.getUserName()), SOURCE_TYPE,
                    profileSource(profile, "userName"));
        }

        graphMapper.expireProfileRelations(expertId);
        int relationCount = 0;
        relationCount += linkProfileValue(expertId, "BELONGS_TO", "Organization",
                profile.getOrganization(), "organization", "档案单位", profile, reviewStatus);
        relationCount += linkProfileValue(expertId, "HAS_CREDENTIAL", "Credential",
                profile.getProfessionalTitle(), "credential", "职称职务", profile, reviewStatus);
        relationCount += linkProfileValue(expertId, "EXPERTISE_IN", "Capability",
                profile.getResearchField(), "researchField", "专业方向", profile, reviewStatus);
        relationCount += linkProfileValue(expertId, "WORKS_IN_INDUSTRY", "Industry",
                profile.getIndustryField(), "industryField", "产业领域", profile, reviewStatus);
        relationCount += linkProfileValue(expertId, "SERVES_REGION", "ServiceRegion",
                profile.getServiceRegion(), "serviceRegion", "服务区域", profile, reviewStatus);
        relationCount += projectCertifications(expertId, profile);

        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("userId", userId);
        eventPayload.put("profileId", profile.getProfileId());
        eventPayload.put("relationCount", relationCount);
        outboxService.enqueue("EXPERT_PROJECTED", "EXPERT", userId, eventPayload);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "PROJECTED");
        result.put("userId", userId);
        result.put("expertEntityId", expertId);
        result.put("relationCount", relationCount);
        result.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        result.put("reviewStatus", reviewStatus);
        return result;
    }

    @Transactional
    public void deleteExpertProjection(Long userId)
    {
        graphMapper.deleteExpertEntity(userId);
    }

    private int linkProfileValue(Long expertId, String relationType, String entityType,
            String value, String propertyName, String label, ExpertProfile profile,
            String reviewStatus)
    {
        if (value == null || value.isBlank())
        {
            return 0;
        }
        String canonicalName = value.trim();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", canonicalName);
        properties.put("profileField", propertyName);
        Long targetId = upsertEntity(entityType,
                entityType.toLowerCase(Locale.ROOT) + ":" + sha256(normalize(canonicalName)),
                canonicalName, properties, reviewStatus);
        ontologyService.requireRelationType(relationType, "Expert", entityType);
        linkWithEvidence(expertId, relationType, targetId, reviewStatus,
                Map.of("profileField", propertyName), SOURCE_TYPE,
                profileSource(profile, propertyName), null,
                "专家档案字段：" + label, "profile-projection-v1");
        return 1;
    }

    private int projectCertifications(Long expertId, ExpertProfile profile)
    {
        int relationCount = 0;
        List<Map<String, Object>> angles = platformMapper.selectCertificationStatus(profile.getUserId());
        for (Map<String, Object> angle : angles)
        {
            Object materialIdValue = angle.get("materialId");
            if (materialIdValue == null)
            {
                continue;
            }
            Long materialId = ((Number) materialIdValue).longValue();
            String categoryCode = String.valueOf(angle.get("categoryCode"));
            String angleName = String.valueOf(angle.get("angleName"));
            String materialStatus = String.valueOf(angle.get("reviewStatus"));
            String entityStatus = "APPROVED".equals(materialStatus) ? "ACCEPTED" : "PENDING";

            Map<String, Object> certificationProperties = new LinkedHashMap<>();
            certificationProperties.put("categoryCode", categoryCode);
            certificationProperties.put("angleName", angleName);
            certificationProperties.put("required", "1".equals(String.valueOf(angle.get("requiredFlag"))));
            certificationProperties.put("reviewStatus", materialStatus);
            Long certificationId = upsertEntity("Certification",
                    "certification:" + profile.getUserId() + ":" + categoryCode,
                    angleName, certificationProperties, entityStatus);

            Map<String, Object> materialProperties = new LinkedHashMap<>();
            materialProperties.put("materialId", materialId);
            materialProperties.put("categoryCode", categoryCode);
            materialProperties.put("fileName", angle.get("fileName"));
            materialProperties.put("fileType", angle.get("fileType"));
            materialProperties.put("versionNo", angle.get("versionNo"));
            materialProperties.put("reviewStatus", materialStatus);
            Long materialEntityId = upsertEntity("Material", "material:" + materialId,
                    String.valueOf(angle.get("fileName")), materialProperties, entityStatus);

            graphMapper.expireCertificationMaterialRelations(certificationId);
            String sourceRef = "material:" + materialId + ":review";
            String excerpt = "认证角度：" + angleName + "；材料审核状态：" + materialStatus;
            linkWithEvidence(expertId, "HAS_CERTIFICATION", certificationId, entityStatus,
                    Map.of("categoryCode", categoryCode), "MATERIAL", sourceRef, materialId,
                    excerpt, "certification-projection-v1");
            linkWithEvidence(certificationId, "CERTIFIED_BY", materialEntityId, entityStatus,
                    Map.of("categoryCode", categoryCode), "MATERIAL", sourceRef, materialId,
                    excerpt, "certification-projection-v1");
            relationCount += 2;
        }
        return relationCount;
    }

    private void linkWithEvidence(Long sourceId, String relationType, Long targetId,
            String reviewStatus, Map<String, Object> relationProperties,
            String sourceType, String sourceRef, Long materialId,
            String excerpt, String extractorVersion)
    {
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("sourceEntityId", sourceId);
        relation.put("relationType", relationType);
        relation.put("targetEntityId", targetId);
        relation.put("confidence", 1.0);
        relation.put("reviewStatus", reviewStatus);
        relation.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        relation.put("sourceKind", "profile-projection-v1".equals(extractorVersion)
                ? SOURCE_KIND : "CERTIFICATION_PROJECTION");
        relation.put("propertiesJson", JSON.toJSONString(relationProperties));
        graphMapper.upsertRelation(relation);
        Long relationId = graphMapper.selectRelationId(sourceId, relationType, targetId);

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("evidenceKey", sha256(String.join("|",
                String.valueOf(relationId), sourceType, sourceRef)));
        evidence.put("relationId", relationId);
        evidence.put("entityId", null);
        evidence.put("sourceType", sourceType);
        evidence.put("sourceRef", sourceRef);
        evidence.put("materialId", materialId);
        evidence.put("pageNo", null);
        evidence.put("charStart", null);
        evidence.put("charEnd", null);
        evidence.put("quoteHash", sha256(sourceRef));
        evidence.put("excerpt", excerpt);
        evidence.put("confidence", 1.0);
        evidence.put("extractorVersion", extractorVersion);
        graphMapper.upsertEvidence(evidence);
    }

    private Long upsertEntity(String entityType, String externalKey, String canonicalName,
            Map<String, Object> properties, String reviewStatus)
    {
        ontologyService.requireEntityType(entityType);
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityType", entityType);
        entity.put("externalKey", externalKey);
        entity.put("canonicalName", canonicalName);
        entity.put("normalizedName", normalize(canonicalName));
        entity.put("propertiesJson", JSON.toJSONString(properties));
        entity.put("reviewStatus", reviewStatus);
        entity.put("ontologyVersion", KnowledgeGraphOntologyService.ONTOLOGY_VERSION);
        graphMapper.upsertEntity(entity);
        Long entityId = graphMapper.selectEntityIdByExternalKey(entityType, externalKey);
        if (entityId == null)
        {
            throw new ServiceException("知识图谱实体写入后无法读取：" + entityType);
        }
        return entityId;
    }

    private static String requireName(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String profileSource(ExpertProfile profile, String field)
    {
        return "expert_profile:" + profile.getProfileId() + ":" + field;
    }

    private static String normalize(String value)
    {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；：、（）()【】\\[\\]]+", "");
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
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
