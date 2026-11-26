-- 将 expert2 已生成的16个DRAFT维度发布为部分正式画像（MySQL 8+，可重复执行）。
USE science_expert;

START TRANSACTION;

SET @partial_expert_id = (
    SELECT user_id
    FROM sys_user
    WHERE user_name = 'expert2'
      AND del_flag = '0'
    LIMIT 1
);
SET @old_graph_version = (
    SELECT coalesce(graph_version, 0)
    FROM expert_profile
    WHERE user_id = @partial_expert_id
    LIMIT 1
);
SET @available_dimensions = (
    SELECT count(1)
    FROM expert_graph_score
    WHERE user_id = @partial_expert_id
      AND graph_status = 'DRAFT'
      AND profile_kind = 'EVIDENCE_SCORE'
      AND conflict_flag = '0'
);
SET @total_dimensions = (
    SELECT count(1)
    FROM expert_dimension_definition
    WHERE enabled = '0'
);
SET @should_publish_partial = (
    @old_graph_version = 0
    AND @available_dimensions > 0
);
SET @new_graph_version = IF(
    @should_publish_partial,
    @old_graph_version + 1,
    @old_graph_version
);
SET @latest_release_id = (
    SELECT release_id
    FROM expert_release_version
    WHERE user_id = @partial_expert_id
    ORDER BY version_no DESC
    LIMIT 1
);

DELETE FROM expert_graph_score
WHERE user_id = @partial_expert_id
  AND graph_status = 'APPROVED'
  AND @should_publish_partial;

UPDATE expert_graph_score
SET graph_status = 'APPROVED',
    graph_version = @new_graph_version,
    update_time = now()
WHERE user_id = @partial_expert_id
  AND graph_status = 'DRAFT'
  AND profile_kind = 'EVIDENCE_SCORE'
  AND conflict_flag = '0'
  AND @should_publish_partial;

DELETE FROM expert_graph_dimension_evidence
WHERE user_id = @partial_expert_id
  AND graph_status = 'APPROVED'
  AND @should_publish_partial;

UPDATE expert_graph_dimension_evidence
SET graph_status = 'APPROVED'
WHERE user_id = @partial_expert_id
  AND graph_status = 'DRAFT'
  AND @should_publish_partial;

UPDATE expert_profile
SET graph_version = @new_graph_version,
    update_time = now()
WHERE user_id = @partial_expert_id
  AND coalesce(graph_version, 0) = 0
  AND @should_publish_partial;

UPDATE expert_release_version
SET graph_version = @new_graph_version,
    profile_snapshot_json = json_set(
        profile_snapshot_json,
        '$.status', 'PARTIAL',
        '$.graphVersion', @new_graph_version,
        '$.profile.graphVersion', @new_graph_version,
        '$.availableDimensionCount', @available_dimensions,
        '$.insufficientDimensionCount',
        greatest(@total_dimensions - @available_dimensions, 0)
    ),
    approved_by = '系统迁移',
    trigger_type = 'PARTIAL_PROFILE_BACKFILL'
WHERE release_id = @latest_release_id
  AND coalesce(graph_version, 0) = 0
  AND @should_publish_partial;

COMMIT;

SELECT
    @partial_expert_id AS expert_user_id,
    @old_graph_version AS old_graph_version,
    @new_graph_version AS new_graph_version,
    @available_dimensions AS available_dimensions,
    @total_dimensions AS total_dimensions,
    greatest(@total_dimensions - @available_dimensions, 0)
        AS insufficient_dimensions,
    @latest_release_id AS updated_release_id;
