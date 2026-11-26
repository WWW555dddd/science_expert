-- 三类待审核业务流程演示数据（MySQL 8+，可重复执行）。
-- 1. 业务经办人员：专家已接受任务，等待经办人员确认正式安排。
-- 2. 管理员：待审批的画像评价。
-- 3. 管理员：待处理的风险预警。
USE science_expert;

START TRANSACTION;

SET @demo_expert_user_id = coalesce(
    (
        SELECT user_id
        FROM expert_profile
        WHERE user_id = 106
          AND review_status = 'APPROVED'
          AND del_flag = '0'
        LIMIT 1
    ),
    (
        SELECT user_id
        FROM expert_profile
        WHERE review_status = 'APPROVED'
          AND service_status = 'AVAILABLE'
          AND del_flag = '0'
        ORDER BY user_id
        LIMIT 1
    )
);

SET @demo_service_region = coalesce(
    (
        SELECT nullif(service_region, '')
        FROM expert_profile
        WHERE user_id = @demo_expert_user_id
        LIMIT 1
    ),
    '江西省'
);
SET @demo_industry = coalesce(
    (
        SELECT nullif(industry_field, '')
        FROM expert_profile
        WHERE user_id = @demo_expert_user_id
        LIMIT 1
    ),
    '人工智能'
);

SET @demo_schedule_start = coalesce(
    (
        SELECT start_time
        FROM expert_schedule
        WHERE user_id = @demo_expert_user_id
          AND weekly_capacity > 0
          AND end_time > start_time
        ORDER BY weekday, start_time
        LIMIT 1
    ),
    '07:30:00'
);
SET @demo_schedule_end = coalesce(
    (
        SELECT end_time
        FROM expert_schedule
        WHERE user_id = @demo_expert_user_id
          AND weekly_capacity > 0
          AND end_time > start_time
        ORDER BY weekday, start_time
        LIMIT 1
    ),
    '09:30:00'
);
SET @demo_weekday = coalesce(
    (
        SELECT weekday
        FROM expert_schedule
        WHERE user_id = @demo_expert_user_id
          AND weekly_capacity > 0
          AND end_time > start_time
        ORDER BY weekday, start_time
        LIMIT 1
    ),
    1
);
SET @demo_days_ahead = mod(
    @demo_weekday - (weekday(curdate()) + 1) + 7,
    7
);
SET @demo_days_ahead = if(@demo_days_ahead = 0, 7, @demo_days_ahead) + 14;
SET @demo_service_day = date_add(curdate(), interval @demo_days_ahead day);
SET @demo_service_start = timestamp(@demo_service_day, @demo_schedule_start);
SET @demo_service_end = timestamp(@demo_service_day, @demo_schedule_end);

-- 一、业务经办人员待确认正式安排。
DELETE FROM expert_task_conflict_confirmation
WHERE external_task_id = 'DEMO-BUSINESS-ACCEPTANCE-20261123-001';

INSERT INTO expert_task_conflict_confirmation
    (external_task_id, expert_user_id, service_start, service_end,
     service_mode, service_region, industry, conflict_status, status,
     arrangement_status, statement, requested_by, request_time, confirmed_at,
     source_system, version)
VALUES
    ('DEMO-BUSINESS-ACCEPTANCE-20261123-001', @demo_expert_user_id,
     @demo_service_start, @demo_service_end, 'REMOTE',
     @demo_service_region, @demo_industry, 'NONE', 'ACCEPTED',
     'NOT_CONFIRMED',
     '模拟专家已接受本次任务，并确认不存在利益冲突，等待业务经办人员确认正式安排。',
     'admin', date_sub(now(), interval 1 hour), now(), 'demo-review-seed', 1);

DELETE FROM expert_business_context_snapshot
WHERE external_task_id = 'DEMO-BUSINESS-ACCEPTANCE-20261123-001';

INSERT INTO expert_business_context_snapshot
    (expert_user_id, external_task_id, task_type, service_start, service_end,
     service_mode, service_region, industry, occupancy_status, conflict_status,
     source_system, source_kind, source_version, verified_at, create_by,
     create_time)
VALUES
    (@demo_expert_user_id, 'DEMO-BUSINESS-ACCEPTANCE-20261123-001',
     'DEMO_BUSINESS_ACCEPTANCE', @demo_service_start, @demo_service_end,
     'REMOTE', @demo_service_region, @demo_industry, 'FREE', 'NONE',
     'demo-review-seed', 'MOCK', 'business-acceptance-pending-arrangement',
     now(), 'seed', now());

-- 二、管理员待审批的画像评价。
INSERT INTO expert_service_evidence
    (source_event_id, expert_user_id, evidence_type, external_task_id,
     source_system, source_kind, occurred_at, title, summary, raw_payload,
     source_digest, processing_status, received_by, create_by, create_time)
VALUES
    ('DEMO-PROFILE-REVIEW-20261123-001', @demo_expert_user_id,
     'SERVICE_REPORT', 'DEMO-PROFILE-TASK-20261123-001',
     'demo-review-seed', 'MOCK', date_sub(now(), interval 2 hour),
     '画像评价模拟服务报告',
     '模拟服务按期完成，成果完整并得到需求方认可，建议上调报告质量与同类项目经验维度。',
     json_object('taskId', 'DEMO-PROFILE-TASK-20261123-001',
                 'result', 'GOOD'),
     'demo-profile-review-seed-001', 'SUGGESTED',
     'seed', 'seed', now())
ON DUPLICATE KEY UPDATE
    expert_user_id = values(expert_user_id),
    occurred_at = values(occurred_at),
    title = values(title),
    summary = values(summary),
    raw_payload = values(raw_payload),
    processing_status = 'SUGGESTED',
    update_time = now();

SET @demo_profile_evidence_id = (
    SELECT evidence_id
    FROM expert_service_evidence
    WHERE source_event_id = 'DEMO-PROFILE-REVIEW-20261123-001'
    LIMIT 1
);

DELETE FROM expert_profile_update_suggestion
WHERE evidence_id = @demo_profile_evidence_id;

INSERT INTO expert_profile_update_suggestion
    (evidence_id, user_id, status, summary, confidence, version,
     create_time, update_time)
VALUES
    (@demo_profile_evidence_id, @demo_expert_user_id, 'PENDING',
     '模拟服务报告呈现正向反馈，建议小幅上调历史报告质量和同类项目经验；当前记录等待管理员审批。',
     0.82, 0, now(), now());

SET @demo_suggestion_id = last_insert_id();

INSERT INTO expert_profile_update_suggestion_item
    (suggestion_id, dimension_code, dimension_name, before_score,
     suggested_score, reason, evidence, confidence, create_time)
VALUES
    (@demo_suggestion_id, 'PERF_REPORT_QUALITY', '历史报告质量', 80, 84,
     '模拟报告按期完成、结构完整且获得需求方认可，建议小幅上调。',
     '模拟画像评价服务报告：成果按期交付，报告结构完整，需求方评价良好。',
     0.84, now()),
    (@demo_suggestion_id, 'PERF_SIMILAR_PROJECT', '同类项目经验', 72, 76,
     '模拟任务形成了新的同类项目履历，建议补充画像证据。',
     '模拟画像评价服务报告：已完成相近产业方向的科技保险专家服务。',
     0.78, now());

-- 三、管理员待处理的风险预警。
INSERT INTO expert_risk_review
    (evidence_id, source_event_id, expert_user_id, risk_type, severity,
     title, summary, status, reviewer_id, reviewer_name, review_comment,
     source_system, source_kind, occurred_at, version, create_time,
     update_time, resolved_time)
VALUES
    (NULL, 'DEMO-RISK-REVIEW-20261123-001', @demo_expert_user_id,
     'LOW_EVALUATION', 'MEDIUM', '模拟低评价风险预警',
     '需求方反馈本次模拟服务响应偏慢，需要管理员核实并记录处理结论；当前预警仍待审核。',
     'OPEN', NULL, NULL, NULL, 'demo-review-seed', 'MOCK',
     date_sub(now(), interval 30 minute), 0, now(), now(), NULL)
ON DUPLICATE KEY UPDATE
    expert_user_id = values(expert_user_id),
    severity = values(severity),
    title = values(title),
    summary = values(summary),
    status = 'OPEN',
    reviewer_id = NULL,
    reviewer_name = NULL,
    review_comment = NULL,
    source_system = values(source_system),
    source_kind = values(source_kind),
    occurred_at = values(occurred_at),
    version = 0,
    update_time = now(),
    resolved_time = NULL;

COMMIT;

SELECT
    @demo_expert_user_id AS expert_user_id,
    'DEMO-BUSINESS-ACCEPTANCE-20261123-001' AS business_review_task_id,
    @demo_suggestion_id AS pending_profile_suggestion_id,
    (
        SELECT risk_id
        FROM expert_risk_review
        WHERE source_event_id = 'DEMO-RISK-REVIEW-20261123-001'
        LIMIT 1
    ) AS pending_risk_review_id,
    @demo_service_start AS demo_task_service_start,
    @demo_service_end AS demo_task_service_end;
