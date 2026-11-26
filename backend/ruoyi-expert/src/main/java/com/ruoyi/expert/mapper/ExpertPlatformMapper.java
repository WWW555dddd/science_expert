package com.ruoyi.expert.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.expert.domain.ExpertGraphDimension;
import com.ruoyi.expert.domain.ExpertMaterial;
import com.ruoyi.expert.domain.ExpertMaterialFile;
import com.ruoyi.expert.domain.ExpertObjectCleanupTask;
import com.ruoyi.expert.domain.ExpertProfile;
import com.ruoyi.expert.domain.ExpertSchedule;
import com.ruoyi.expert.domain.ExpertStatusRequest;
import com.ruoyi.expert.domain.ExpertUnavailablePeriod;

/**
 * 专家平台数据访问。
 */
public interface ExpertPlatformMapper
{
    ExpertProfile selectProfileByUserId(Long userId);

    ExpertProfile selectProfileByUserIdForUpdate(Long userId);

    ExpertProfile selectAdminProfileByUserId(Long userId);

    ExpertProfile selectAdminProfileByUserIdForUpdate(Long userId);

    int countDeletedProfileByUserId(Long userId);

    int upsertProfile(ExpertProfile profile);

    int upsertAdminProfile(ExpertProfile profile);

    int syncApprovedProfile(ExpertProfile profile);

    List<Map<String, Object>> selectAdminExpertList(Map<String, Object> query);

    List<Map<String, Object>> selectExpertRoleUsers();

    int countExpertRoleUser(Long userId);

    int softDeleteExpertProfile(@Param("userId") Long userId);

    int deleteExpertProfile(@Param("userId") Long userId);

    int deleteMaterialsByUserId(@Param("userId") Long userId);

    int deleteFieldMappingsByUserId(@Param("userId") Long userId);

    int deleteReviewLogsByUserId(@Param("userId") Long userId);

    int deleteStatusLogsByUserId(@Param("userId") Long userId);

    int deleteStatusRequestsByUserId(@Param("userId") Long userId);

    int deleteGraphScoresByUserId(@Param("userId") Long userId);

    int deleteModelCallLogsByUserId(@Param("userId") Long userId);

    int deleteApprovedExpert(@Param("userId") Long userId);

    List<Map<String, Object>> selectExpertReleaseVersions(Long userId);

    Map<String, Object> selectExpertReleaseVersion(@Param("userId") Long userId,
            @Param("releaseId") Long releaseId);

    Map<String, Object> selectLatestExpertReleaseVersion(Long userId);

    int insertExpertReleaseVersion(Map<String, Object> release);

    int updateProfileCompletion(@Param("userId") Long userId, @Param("completion") Integer completion);

    int updateProfileReviewStatus(@Param("userId") Long userId, @Param("status") String status);

    int markProfilePendingIfNotApproved(Long userId);

    int touchProfileSubmitTime(Long userId);

    int updateProfileServiceStatus(@Param("userId") Long userId, @Param("status") String status);

    int updateProfileServiceStatusIfCurrent(@Param("userId") Long userId,
            @Param("sourceStatus") String sourceStatus, @Param("status") String status);

    int updateCreditScore(@Param("userId") Long userId, @Param("creditScore") Integer creditScore);

    int insertCreditLog(@Param("userId") Long userId,
            @Param("changeType") String changeType,
            @Param("riskId") Long riskId,
            @Param("requestId") Long requestId,
            @Param("beforeScore") Integer beforeScore,
            @Param("afterScore") Integer afterScore,
            @Param("delta") Integer delta,
            @Param("reason") String reason,
            @Param("operatorId") Long operatorId,
            @Param("operatorName") String operatorName);

    List<Map<String, Object>> selectCreditLogsByUserId(@Param("userId") Long userId);

    List<ExpertSchedule> selectScheduleByUserId(Long userId);

    int deleteScheduleByUserId(Long userId);

    int insertSchedule(ExpertSchedule schedule);

    List<ExpertUnavailablePeriod> selectUnavailablePeriodsByUserId(Long userId);

    ExpertUnavailablePeriod selectUnavailablePeriodById(Long unavailableId);

    int insertUnavailablePeriod(ExpertUnavailablePeriod period);

    int updateUnavailablePeriod(ExpertUnavailablePeriod period);

    int deleteUnavailablePeriod(@Param("unavailableId") Long unavailableId,
            @Param("userId") Long userId);

    int deleteUnavailablePeriodsByUserId(Long userId);

    List<ExpertMaterial> selectMaterialList(ExpertMaterial query);

    ExpertMaterial selectMaterialById(Long materialId);

    ExpertMaterial selectMaterialByIdForUpdate(Long materialId);

    List<ExpertMaterialFile> selectMaterialFiles(@Param("materialId") Long materialId);

    ExpertMaterialFile selectMaterialFile(@Param("materialId") Long materialId,
            @Param("fileId") Long fileId);

    int countMaterialFiles(@Param("materialId") Long materialId);

    int insertMaterialFile(ExpertMaterialFile file);

    int deleteMaterialFile(@Param("materialId") Long materialId,
            @Param("fileId") Long fileId);

    int syncMaterialPrimaryFile(@Param("materialId") Long materialId,
            @Param("file") ExpertMaterialFile file, @Param("updateBy") String updateBy);

    int updateMaterialValidity(@Param("materialId") Long materialId,
            @Param("validFrom") String validFrom, @Param("validUntil") String validUntil,
            @Param("updateBy") String updateBy);

    int prepareMaterialForReview(@Param("materialId") Long materialId,
            @Param("updateBy") String updateBy);

    ExpertMaterial selectLatestMaterialByUserId(Long userId);

    ExpertMaterial selectLatestMaterialByUserIdAndCategory(@Param("userId") Long userId,
            @Param("categoryCode") String categoryCode);

    ExpertMaterial selectLatestMaterialByUserIdAndCategoryForUpdate(@Param("userId") Long userId,
            @Param("categoryCode") String categoryCode);

    List<Map<String, Object>> selectEffectiveMaterialVersions(Long userId);

    List<Map<String, Object>> selectCertificationStatus(Long userId);

    int countRequiredAngles();

    int countApprovedRequiredAngles(Long userId);

    int countAgentReadyRequiredAngles(Long userId);

    int selectMaxMaterialVersion(@Param("userId") Long userId, @Param("categoryCode") String categoryCode);

    int insertMaterial(ExpertMaterial material);

    int insertAdminApprovedMaterial(ExpertMaterial material);

    int claimMaterialJob(@Param("materialId") Long materialId,
            @Param("processingToken") String processingToken);

    int releaseStaleMaterialJobs();

    List<Long> selectRecoverableQueuedMaterialIds(@Param("limit") int limit);

    int updateMaterialJob(@Param("materialId") Long materialId,
            @Param("processingToken") String processingToken, @Param("jobStatus") String jobStatus,
            @Param("reviewStatus") String reviewStatus, @Param("extractedFields") Integer extractedFields,
            @Param("issueCount") Integer issueCount, @Param("message") String message);

    int updateMaterialReview(@Param("materialId") Long materialId, @Param("reviewStatus") String reviewStatus,
            @Param("comment") String comment);

    int deleteFieldMappings(Long materialId);

    int insertFieldMapping(@Param("materialId") Long materialId, @Param("dbField") String dbField,
            @Param("fileField") String fileField, @Param("extractedValue") String extractedValue,
            @Param("confidence") Double confidence, @Param("evidence") String evidence,
            @Param("conflictFlag") String conflictFlag);

    List<Map<String, Object>> selectFieldMappings(Long materialId);

    int updateFieldMappingReview(@Param("materialId") Long materialId,
            @Param("mappingId") Long mappingId,
            @Param("reviewStatus") String reviewStatus, @Param("comment") String comment,
            @Param("reviewerName") String reviewerName);

    int updateFieldMappingOpinion(@Param("materialId") Long materialId,
            @Param("mappingId") Long mappingId,
            @Param("reviewedValue") String reviewedValue,
            @Param("reviewedConfidence") Double reviewedConfidence,
            @Param("reviewedEvidence") String reviewedEvidence,
            @Param("reviewComment") String reviewComment,
            @Param("reviewerName") String reviewerName);

    int deleteDraftGraph(Long userId);

    int deleteDimensionAssessmentsByMaterial(Long materialId);

    int upsertDimensionAssessment(Map<String, Object> assessment);

    List<Map<String, Object>> selectDimensionAssessments(@Param("userId") Long userId,
            @Param("acceptedOnly") boolean acceptedOnly);

    List<Map<String, Object>> selectMaterialDimensionAssessments(
            @Param("materialId") Long materialId);

    int updateDimensionAssessmentReviewById(@Param("materialId") Long materialId,
            @Param("assessmentId") Long assessmentId,
            @Param("reviewStatus") String reviewStatus);

    int updateDimensionAssessmentReview(@Param("materialId") Long materialId,
            @Param("reviewStatus") String reviewStatus);

    int updateDimensionAssessmentOpinion(@Param("materialId") Long materialId,
            @Param("assessmentId") Long assessmentId,
            @Param("reviewedScore") Double reviewedScore,
            @Param("reviewedEvidence") String reviewedEvidence,
            @Param("reviewComment") String reviewComment,
            @Param("reviewerName") String reviewerName);

    int countDraftGraph(Long userId);

    int countEnabledGraphDimensions();

    int deleteApprovedGraph(Long userId);

    int deleteApprovedDimensionEvidence(Long userId);

    int insertGraphScore(@Param("userId") Long userId, @Param("dimensionCode") String dimensionCode,
            @Param("score") Double score, @Param("confidence") Double confidence,
            @Param("evidence") String evidence, @Param("status") String status,
            @Param("graphVersion") Integer graphVersion, @Param("profileKind") String profileKind,
            @Param("conflictFlag") String conflictFlag,
            @Param("inferenceMethod") String inferenceMethod,
            @Param("sourceCount") Integer sourceCount,
            @Param("graphRelationIds") String graphRelationIds,
            @Param("graphEntityIds") String graphEntityIds,
            @Param("graphEvidenceIds") String graphEvidenceIds,
            @Param("graphPathCount") Integer graphPathCount,
            @Param("profileRuleVersion") String profileRuleVersion);

    int deleteDraftDimensionEvidence(Long userId);

    int deleteDimensionEvidenceByMaterial(@Param("materialId") Long materialId,
            @Param("graphStatus") String graphStatus);

    int insertDimensionEvidence(Map<String, Object> evidence);

    List<Map<String, Object>> selectLatestDimensionEvidence(@Param("userId") Long userId,
            @Param("graphStatus") String graphStatus);

    int approveDraftGraph(Long userId);

    int approveDraftGraphEvidence(Long userId);

    int incrementGraphVersion(Long userId);

    List<ExpertGraphDimension> selectGraphDimensions(@Param("userId") Long userId,
            @Param("status") String status);

    int insertReviewLog(@Param("userId") Long userId, @Param("materialId") Long materialId,
            @Param("action") String action, @Param("reviewerId") Long reviewerId,
            @Param("reviewerName") String reviewerName, @Param("previousStatus") String previousStatus,
            @Param("currentStatus") String currentStatus, @Param("comment") String comment);

    List<Map<String, Object>> selectReviewLogs(Long userId);

    int insertStatusLog(@Param("userId") Long userId, @Param("previousStatus") String previousStatus,
            @Param("currentStatus") String currentStatus, @Param("reason") String reason,
            @Param("creditRelated") String creditRelated, @Param("operatorId") Long operatorId,
            @Param("operatorName") String operatorName);

    List<Map<String, Object>> selectStatusLogs(Long userId);

    int insertStatusRequest(ExpertStatusRequest request);

    ExpertStatusRequest selectStatusRequestById(Long requestId);

    ExpertStatusRequest selectStatusRequestByIdForUpdate(Long requestId);

    ExpertStatusRequest selectPendingStatusRequestByUserId(Long userId);

    List<ExpertStatusRequest> selectStatusRequestsByUserId(Long userId);

    List<Map<String, Object>> selectStatusRequests(Map<String, Object> query);

    int resolveStatusRequest(@Param("requestId") Long requestId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("requestStatus") String requestStatus,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewerName") String reviewerName,
            @Param("reviewComment") String reviewComment);

    int cancelStatusRequest(@Param("requestId") Long requestId, @Param("userId") Long userId,
            @Param("expectedVersion") Integer expectedVersion,
            @Param("operatorName") String operatorName,
            @Param("comment") String comment);

    int cancelPendingStatusRequests(@Param("userId") Long userId,
            @Param("operatorName") String operatorName, @Param("comment") String comment);

    int countPendingStatusRequests();

    int upsertApprovedExpert(@Param("profile") ExpertProfile profile, @Param("materialId") Long materialId,
            @Param("approvedBy") String approvedBy, @Param("snapshotJson") String snapshotJson);

    List<Map<String, Object>> selectReviewQueue(Map<String, Object> query);

    Map<String, Object> selectReviewDetail(Long userId);

    List<Map<String, Object>> selectApprovedExperts(Map<String, Object> query);

    int countApprovedExperts();

    int countQueue();

    int countAgentFailures();

    int countPausedExperts();

    List<Map<String, Object>> selectProcessingJobs(Map<String, Object> query);

    int resetMaterialJob(Long materialId);

    int insertObjectCleanupTask(String objectUri);

    List<ExpertObjectCleanupTask> selectObjectCleanupTasks(@Param("limit") int limit);

    int markObjectCleanupDone(Long cleanupId);

    int markObjectCleanupRetry(@Param("cleanupId") Long cleanupId,
            @Param("lastError") String lastError);

    List<Map<String, Object>> selectQualificationReminderCandidates(@Param("days") int days);

    int insertQualificationReminder(Map<String, Object> reminder);

    int resolveStaleQualificationReminders(@Param("days") int days);

    List<Map<String, Object>> selectQualificationReminders(@Param("userId") Long userId);

    int resolveQualificationRemindersByMaterial(@Param("materialId") Long materialId);

    int resolveQualificationReminderTypeChange(@Param("materialId") Long materialId,
            @Param("currentType") String currentType);

    int insertModelCallLog(Map<String, Object> callLog);
}
