package com.ruoyi.expert.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * MySQL 知识图谱源数据访问。
 */
public interface KnowledgeGraphMapper
{
    Map<String, Object> selectOntologyVersion(@Param("versionCode") String versionCode);

    int upsertEntity(Map<String, Object> entity);

    Long selectEntityIdByExternalKey(@Param("entityType") String entityType,
            @Param("externalKey") String externalKey);

    int upsertEntityAlias(@Param("entityId") Long entityId,
            @Param("aliasName") String aliasName,
            @Param("normalizedAlias") String normalizedAlias,
            @Param("sourceType") String sourceType,
            @Param("sourceRef") String sourceRef);

    int upsertRelation(Map<String, Object> relation);

    int expireProfileRelations(@Param("sourceEntityId") Long sourceEntityId);

    int expireCertificationMaterialRelations(@Param("sourceEntityId") Long sourceEntityId);

    Long selectRelationId(@Param("sourceEntityId") Long sourceEntityId,
            @Param("relationType") String relationType,
            @Param("targetEntityId") Long targetEntityId);

    int upsertEvidence(Map<String, Object> evidence);

    int reviewMaterialFacts(@Param("materialId") Long materialId,
            @Param("reviewStatus") String reviewStatus);

    int acceptMaterialEntities(@Param("materialId") Long materialId);

    int deleteMaterialFactsByUserId(@Param("userId") Long userId);

    int deleteMaterialEvidenceByUserId(@Param("userId") Long userId);

    int insertOutboxEvent(Map<String, Object> event);

    List<Map<String, Object>> selectPendingOutbox(@Param("limit") int limit);

    int claimOutboxEvent(@Param("eventId") String eventId);

    int markOutboxDone(@Param("eventId") String eventId);

    int markOutboxRetry(@Param("eventId") String eventId, @Param("lastError") String lastError);

    int recoverStaleOutbox();

    Map<String, Object> selectExpertEntity(@Param("userId") Long userId);

    Map<String, Object> selectEntityById(@Param("entityId") Long entityId);

    List<Long> selectTaskEntityIds();

    int deleteExpertEntity(@Param("userId") Long userId);

    List<Map<String, Object>> selectEntitiesByIds(@Param("entityIds") List<Long> entityIds);

    List<Map<String, Object>> selectExtractedEntitiesByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectRelationsForEntityIds(
            @Param("entityIds") List<Long> entityIds);

    List<Map<String, Object>> selectEvidenceByRelationIds(
            @Param("relationIds") List<Long> relationIds);

    List<Map<String, Object>> selectEvidenceByEntityIds(
            @Param("entityIds") List<Long> entityIds);

    List<Map<String, Object>> selectCapabilityMappings();

    List<Map<String, Object>> selectDimensionAssessments(
            @Param("userId") Long userId,
            @Param("acceptedOnly") boolean acceptedOnly);

    int deleteMaterialAssessments(@Param("materialId") Long materialId);

    int deleteMaterialAssessmentRelations(@Param("materialId") Long materialId);

    int deleteMaterialAssessmentEntities(@Param("materialId") Long materialId);
}
