-- 专家开放接口模拟数据（MySQL 8+，可重复执行）。
-- 使用第一个已认证且正常接单的专家作为模拟数据所属专家。
USE science_expert;

SET @mock_expert_user_id = (
    SELECT user_id
    FROM expert_profile
    WHERE review_status = 'APPROVED'
      AND service_status = 'AVAILABLE'
      AND del_flag = '0'
    ORDER BY user_id
    LIMIT 1
);

SET @mock_schedule = (
    SELECT schedule_id
    FROM expert_schedule
    WHERE user_id = @mock_expert_user_id
      AND weekly_capacity > 0
      AND end_time > start_time
    ORDER BY weekday, start_time
    LIMIT 1
);

SET @mock_weekday = (
    SELECT weekday FROM expert_schedule WHERE schedule_id = @mock_schedule
);
SET @mock_start_time = (
    SELECT start_time FROM expert_schedule WHERE schedule_id = @mock_schedule
);
SET @mock_end_time = (
    SELECT end_time FROM expert_schedule WHERE schedule_id = @mock_schedule
);
SET @mock_mode = coalesce((
    SELECT service_mode FROM expert_schedule WHERE schedule_id = @mock_schedule
), 'BOTH');
SET @mock_days_ahead = MOD(@mock_weekday - (WEEKDAY(CURDATE()) + 1) + 7, 7);
SET @mock_days_ahead = IF(@mock_days_ahead = 0, 7, @mock_days_ahead);
SET @mock_free_day = DATE_ADD(CURDATE(), INTERVAL @mock_days_ahead DAY);
SET @mock_free_start = TIMESTAMP(@mock_free_day, @mock_start_time);
SET @mock_free_end = TIMESTAMP(@mock_free_day, @mock_end_time);

INSERT INTO expert_business_context_snapshot
    (expert_user_id, external_task_id, task_type, service_start, service_end,
     service_mode, service_region, industry, occupancy_status, conflict_status,
     source_system, source_kind, source_version, verified_at, create_by,
     create_time)
SELECT
    @mock_expert_user_id, NULL, 'DEMO_ELIGIBILITY', @mock_free_start, @mock_free_end,
    @mock_mode, NULL, NULL, 'FREE', 'NONE',
    'mock-dispatch', 'MOCK', 'seed-20261119-free', NOW(), 'seed', NOW()
FROM DUAL
WHERE @mock_expert_user_id IS NOT NULL
  AND @mock_schedule IS NOT NULL
ON DUPLICATE KEY UPDATE
    external_task_id = VALUES(external_task_id),
    task_type = VALUES(task_type),
    service_start = VALUES(service_start),
    service_end = VALUES(service_end),
    service_mode = VALUES(service_mode),
    service_region = VALUES(service_region),
    industry = VALUES(industry),
    occupancy_status = VALUES(occupancy_status),
    conflict_status = VALUES(conflict_status),
    source_kind = VALUES(source_kind),
    verified_at = VALUES(verified_at),
    update_time = NOW();
INSERT IGNORE INTO expert_service_evidence
    (source_event_id, expert_user_id, evidence_type, external_task_id,
     source_system, source_kind, occurred_at, title, summary, raw_payload,
     source_digest, processing_status, received_by, create_by, create_time)
SELECT
    'MOCK-EVIDENCE-20261119-001', @mock_expert_user_id, 'SERVICE_REPORT',
    'MOCK-TASK-COMPLETED-001', 'mock-dispatch', 'MOCK',
    DATE_SUB(NOW(), INTERVAL 1 DAY), '模拟科技保险专家服务报告',
    '用于验证服务报告接收入口，不参与正式画像自动更新。',
    JSON_OBJECT('taskId', 'MOCK-TASK-COMPLETED-001',
                'reportQuality', 'GOOD', 'creditConclusion', NULL),
    'mock-seed-digest-001', 'RECEIVED', 'seed', 'seed', NOW()
FROM DUAL
WHERE @mock_expert_user_id IS NOT NULL;

-- 第二位已认证专家 expert2 的业务快照。
SET @mock_expert2_user_id = (
    SELECT user_id
    FROM expert_profile
    WHERE review_status = 'APPROVED'
      AND service_status = 'AVAILABLE'
      AND del_flag = '0'
      AND user_id <> @mock_expert_user_id
    ORDER BY user_id
    LIMIT 1
);

SET @mock_expert2_schedule = (
    SELECT schedule_id
    FROM expert_schedule
    WHERE user_id = @mock_expert2_user_id
      AND weekly_capacity > 0
      AND end_time > start_time
    ORDER BY weekday, start_time
    LIMIT 1
);

SET @mock_expert2_weekday = (
    SELECT weekday FROM expert_schedule WHERE schedule_id = @mock_expert2_schedule
);
SET @mock_expert2_start_time = (
    SELECT start_time FROM expert_schedule WHERE schedule_id = @mock_expert2_schedule
);
SET @mock_expert2_end_time = (
    SELECT end_time FROM expert_schedule WHERE schedule_id = @mock_expert2_schedule
);
SET @mock_expert2_mode = coalesce((
    SELECT service_mode FROM expert_schedule WHERE schedule_id = @mock_expert2_schedule
), 'BOTH');
SET @mock_expert2_days_ahead = MOD(
    @mock_expert2_weekday - (WEEKDAY(CURDATE()) + 1) + 7, 7
);
SET @mock_expert2_days_ahead = IF(@mock_expert2_days_ahead = 0, 7, @mock_expert2_days_ahead);
SET @mock_expert2_free_day = DATE_ADD(
    CURDATE(), INTERVAL @mock_expert2_days_ahead DAY
);
SET @mock_expert2_free_start = TIMESTAMP(
    @mock_expert2_free_day, @mock_expert2_start_time
);
SET @mock_expert2_free_end = TIMESTAMP(
    @mock_expert2_free_day, @mock_expert2_end_time
);

INSERT INTO expert_business_context_snapshot
    (expert_user_id, external_task_id, task_type, service_start, service_end,
     service_mode, service_region, industry, occupancy_status, conflict_status,
     source_system, source_kind, source_version, verified_at, create_by,
     create_time)
SELECT
    @mock_expert2_user_id, NULL, 'DEMO_ELIGIBILITY',
    @mock_expert2_free_start, @mock_expert2_free_end,
    @mock_expert2_mode, NULL, NULL, 'FREE', 'NONE',
    'mock-dispatch', 'MOCK', 'seed-expert2-free', NOW(), 'seed', NOW()
FROM DUAL
WHERE @mock_expert2_user_id IS NOT NULL
  AND @mock_expert2_schedule IS NOT NULL
ON DUPLICATE KEY UPDATE
    external_task_id = VALUES(external_task_id),
    task_type = VALUES(task_type),
    service_start = VALUES(service_start),
    service_end = VALUES(service_end),
    service_mode = VALUES(service_mode),
    service_region = VALUES(service_region),
    industry = VALUES(industry),
    occupancy_status = VALUES(occupancy_status),
    conflict_status = VALUES(conflict_status),
    source_kind = VALUES(source_kind),
    verified_at = VALUES(verified_at),
    update_time = NOW();
