-- 清理接单测试数据并生成唯一标准业务任务上下文（MySQL 8+）。
USE science_expert;

START TRANSACTION;

DELETE FROM expert_task_conflict_confirmation
WHERE external_task_id = 'TEST-EXPERT-ACCEPTANCE-001'
   OR external_task_id LIKE 'BUSINESS-%'
   OR external_task_id = 'MOCK-TASK-CONFLICT-001';

DELETE FROM expert_business_context_snapshot
WHERE external_task_id IN (
      'TEST-EXPERT-ACCEPTANCE-001',
      'TEST-EXPERT-ACCEPTANCE-002'
  )
   OR (
      source_kind = 'MOCK'
      AND (
          external_task_id LIKE 'BUSINESS-%'
          OR external_task_id = 'MOCK-TASK-CONFLICT-001'
      )
   );

INSERT INTO expert_business_context_snapshot
    (expert_user_id, external_task_id, task_type, service_start, service_end,
     service_mode, service_region, industry, occupancy_status, conflict_status,
     source_system, source_kind, source_version, verified_at, create_by,
     create_time)
VALUES
    (106, 'TEST-EXPERT-ACCEPTANCE-001', 'EXPERT_ACCEPTANCE_TEST',
     '2026-12-07 07:30:00', '2026-12-07 09:30:00',
     'REMOTE', '江西省', '人工智能', 'FREE', 'NONE',
     'acceptance-test', 'MOCK', 'acceptance-test-free-001',
     NOW(), 'seed', NOW()),
    (106, 'TEST-EXPERT-ACCEPTANCE-002', 'EXPERT_ACCEPTANCE_BLOCKING_COMPARISON',
     '2026-12-07 07:30:00', '2026-12-07 09:30:00',
     'REMOTE', '江西省', '人工智能', 'FREE', 'NONE',
     'acceptance-test', 'MOCK', 'acceptance-test-free-002',
     NOW(), 'seed', NOW());

COMMIT;
