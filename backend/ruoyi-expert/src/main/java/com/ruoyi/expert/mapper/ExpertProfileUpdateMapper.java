package com.ruoyi.expert.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestion;
import com.ruoyi.expert.domain.ExpertProfileUpdateSuggestionItem;

/**
 * 画像更新建议与人工审核。
 */
public interface ExpertProfileUpdateMapper
{
    int insertSuggestion(ExpertProfileUpdateSuggestion suggestion);

    int insertItem(ExpertProfileUpdateSuggestionItem item);

    ExpertProfileUpdateSuggestion selectByEvidenceId(Long evidenceId);

    ExpertProfileUpdateSuggestion selectById(Long suggestionId);

    ExpertProfileUpdateSuggestion selectByIdForUpdate(Long suggestionId);

    List<ExpertProfileUpdateSuggestion> selectList(@Param("status") String status);

    List<ExpertProfileUpdateSuggestion> selectApprovedByUserId(
            @Param("userId") Long userId, @Param("limit") Integer limit);

    List<ExpertProfileUpdateSuggestionItem> selectItems(Long suggestionId);

    int updateItemApprovedScore(@Param("itemId") Long itemId,
            @Param("approvedScore") Double approvedScore);

    int updateReviewedSummary(@Param("suggestionId") Long suggestionId,
            @Param("reviewedSummary") String reviewedSummary);

    int updateItemReviewedReason(@Param("itemId") Long itemId,
            @Param("reviewedReason") String reviewedReason);

    int touchPendingSuggestion(@Param("suggestionId") Long suggestionId,
            @Param("version") Integer version);

    int resolveSuggestion(@Param("suggestionId") Long suggestionId,
            @Param("status") String status,
            @Param("version") Integer version,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewerName") String reviewerName,
            @Param("comment") String comment,
            @Param("releaseId") Long releaseId);

    int updateProfileGraphVersion(@Param("userId") Long userId,
            @Param("graphVersion") Integer graphVersion);
}
