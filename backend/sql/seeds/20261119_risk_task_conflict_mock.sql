-- 风险预警与任务冲突确认演示数据（MySQL 8+，可重复执行）。
USE science_expert;

INSERT IGNORE INTO expert_risk_review
    (evidence_id, source_event_id, expert_user_id, risk_type, severity,
     title, summary, status, source_system, source_kind, occurred_at,
     version, create_time, update_time)
VALUES
    (NULL, 'MOCK-RISK-20261119-001', 106, 'LOW_EVALUATION', 'MEDIUM',
     '模拟低评价预警',
     '需求方对一次模拟服务反馈不满意，认为响应偏慢。该记录仅用于验证风险预警审核流程，不自动改变专家状态。',
     'OPEN', 'mock-evaluation', 'MOCK', '2026-11-19 10:00:00',
     0, NOW(), NOW());

INSERT IGNORE INTO expert_task_conflict_confirmation
    (external_task_id, expert_user_id, service_start, service_end,
     service_mode, service_region, industry, conflict_status, status,
     requested_by, request_time, source_system, version)
VALUES
    ('MOCK-TASK-CONFLICT-001', 106,
     '2026-12-07 07:30:00', '2026-12-07 09:30:00',
     'REMOTE', '江西省南昌市', '新能源', 'PENDING', 'PENDING',
     'business-demo', NOW(), 'mock-dispatch', 0);

INSERT INTO expert_business_context_snapshot
    (expert_user_id, external_task_id, task_type, service_start, service_end,
     service_mode, service_region, industry, occupancy_status, conflict_status,
     source_system, source_kind, source_version, verified_at, create_by,
     create_time)
VALUES
    (106, 'MOCK-TASK-CONFLICT-001', 'CONFLICT_CONFIRMATION_DEMO',
     '2026-12-07 07:30:00', '2026-12-07 09:30:00',
     'REMOTE', '江西省南昌市', '新能源', 'FREE', 'NONE',
     'mock-dispatch', 'MOCK', 'mock-task-conflict-001-free', NOW(), 'seed', NOW())
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
    verified_at = NOW(),
    update_time = NOW();
