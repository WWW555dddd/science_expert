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

    int expireIndustryExpertRelations(@Param("expertEntityId") Long expertEntityId);

    Long selectRelationId(@Param("sourceEntityId") Long sourceEntityId,
            @Param("relationType") String relationType,
            @Param("targetEntityId") Long targetEntityId);

    int upsertEvidence(Map<String, Object> evidence);

    int reviewMaterialFacts(@Param("materialId") Long materialId,
            @Param("reviewStatus") String reviewStatus);

    List<Map<String, Object>> selectMaterialReviewFacts(@Param("materialId") Long materialId);

    int updateMaterialRelationReview(@Param("materialId") Long materialId,
            @Param("relationId") Long relationId,
            @Param("reviewStatus") String reviewStatus);

    int updateMaterialRelationOpinion(@Param("materialId") Long materialId,
            @Param("relationId") Long relationId,
            @Param("reviewedSourceName") String reviewedSourceName,
            @Param("reviewedSourceType") String reviewedSourceType,
            @Param("reviewedRelationType") String reviewedRelationType,
            @Param("reviewedTargetName") String reviewedTargetName,
            @Param("reviewedTargetType") String reviewedTargetType,
            @Param("reviewedExcerpt") String reviewedExcerpt,
            @Param("reviewComment") String reviewComment,
            @Param("reviewerName") String reviewerName);

    int applyRelationSourceReview(@Param("materialId") Long materialId,
            @Param("relationId") Long relationId);

    int applyRelationTargetReview(@Param("materialId") Long materialId,
            @Param("relationId") Long relationId);

    int applyRelationTypeReview(@Param("materialId") Long materialId,
            @Param("relationId") Long relationId);

    int applyMaterialSourceReviews(@Param("materialId") Long materialId);

    int applyMaterialTargetReviews(@Param("materialId") Long materialId);

    int applyMaterialRelationTypeReviews(@Param("materialId") Long materialId);

    int updateRelationReviewStatus(@Param("relationId") Long relationId,
            @Param("reviewStatus") String reviewStatus);

    int reviewMaterialEvidence(@Param("materialId") Long materialId,
            @Param("reviewStatus") String reviewStatus);

    int acceptMaterialEntities(@Param("materialId") Long materialId);

    int deleteMaterialFactsByUserId(@Param("userId") Long userId);

    int deleteMaterialEvidenceByUserId(@Param("userId") Long userId);

    int deleteMaterialGraphEvidence(@Param("materialId") Long materialId);

    int deleteMaterialGraphRelations(@Param("materialId") Long materialId);

    int deleteOrphanMaterialRelations();

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

    List<Map<String, Object>> selectOverviewEntities(@Param("limit") int limit);

    List<Map<String, Object>> selectOverviewRelations(@Param("limit") int limit);

    List<Map<String, Object>> selectExtractedEntitiesByUserId(@Param("userId") Long userId);

    List<Map<String, Object>> selectRelationsForEntityIds(
            @Param("entityIds") List<Long> entityIds);

    List<Map<String, Object>> selectEvidenceByRelationIds(
            @Param("relationIds") List<Long> relationIds);

    List<Map<String, Object>> selectEvidenceByEntityIds(
            @Param("entityIds") List<Long> entityIds);

}
