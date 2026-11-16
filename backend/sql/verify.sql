-- 专家平台只读检查脚本
-- 使用前将测试专家账号替换为实际账号
SET @expert_name = 'expert_test';
SET @uid = (SELECT user_id FROM sys_user WHERE user_name = @expert_name LIMIT 1);

SELECT @expert_name AS expert_account, @uid AS user_id;

-- 1. 用户与角色
SELECT u.user_id, u.user_name, u.nick_name, u.status,
       r.role_id, r.role_name, r.role_key
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.user_id
LEFT JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.user_name = @expert_name;

-- 2. 专家档案
SELECT profile_id, user_id, real_name, organization, professional_title,
       research_field, industry_field, service_region,
       review_status, service_status, profile_completion,
       latest_submit_time, approved_time, graph_version, version,
       create_time, update_time
FROM expert_profile
WHERE user_id = @uid;

-- 3. 可服务时间表
SELECT schedule_id, weekday, start_time, end_time, service_mode,
       weekly_capacity, effective_from
FROM expert_schedule
WHERE user_id = @uid
ORDER BY weekday, start_time;

-- 4. 资料文件与 Agent 处理状态
SELECT material_id, file_name, file_path, material_category,
       version_no, job_status, review_status, processing_token,
       processing_started_at, extracted_fields, issue_count,
       agent_message, review_comment, create_time, update_time
FROM expert_material
WHERE user_id = @uid
ORDER BY material_id DESC;

-- 5. 文件字段与标准字段映射
SELECT fm.material_id, fm.db_field, fm.file_field, fm.extracted_value,
       fm.confidence, fm.evidence, fm.conflict_flag
FROM expert_field_mapping fm
JOIN expert_material m ON m.material_id = fm.material_id
WHERE m.user_id = @uid
ORDER BY fm.material_id DESC, fm.mapping_id;

-- 6. 30维画像状态
SELECT graph_status, profile_kind, COUNT(*) AS dimension_count,
       MIN(graph_version) AS min_version, MAX(graph_version) AS max_version
FROM expert_graph_score
WHERE user_id = @uid
GROUP BY graph_status, profile_kind;

SELECT d.group_name, d.dimension_name, s.score, s.confidence,
       s.graph_status, s.graph_version, s.profile_kind,
       s.conflict_flag, s.inference_method, s.source_count, s.evidence
FROM expert_graph_score s
JOIN expert_dimension_definition d ON d.dimension_code = s.dimension_code
WHERE s.user_id = @uid
ORDER BY s.graph_status, d.sort_no;

SELECT e.dimension_code, e.material_id, e.score, e.confidence,
       e.graph_status, e.inference_method, e.evidence
FROM expert_graph_dimension_evidence e
WHERE e.user_id = @uid
ORDER BY e.graph_status, e.dimension_code, e.material_id;

-- 7. 最近模型调用审计
SELECT provider, model, prompt_version, latency_ms,
       prompt_tokens, completion_tokens, total_tokens,
       call_status, error_code, create_time
FROM expert_model_call_log
WHERE user_id = @uid
ORDER BY call_id DESC
LIMIT 20;

-- 8. 知识图谱投影、关系、证据和 Outbox
SELECT e.entity_id, e.entity_type, e.canonical_name, e.review_status
FROM kg_entity e
WHERE e.external_key = CONCAT('expert:', @uid);

SELECT r.relation_type, se.canonical_name AS source_name,
       te.canonical_name AS target_name, r.review_status
FROM kg_relation r
JOIN kg_entity se ON se.entity_id = r.source_entity_id
JOIN kg_entity te ON te.entity_id = r.target_entity_id
WHERE se.external_key = CONCAT('expert:', @uid);

SELECT e.source_type, e.source_ref, e.excerpt, e.confidence
FROM kg_evidence e
JOIN kg_relation r ON r.relation_id = e.relation_id
JOIN kg_entity se ON se.entity_id = r.source_entity_id
WHERE se.external_key = CONCAT('expert:', @uid);

SELECT event_type, aggregate_type, aggregate_id, event_status, create_time
FROM kg_change_outbox
ORDER BY create_time DESC
LIMIT 20;

-- 9. 最近 GraphRAG Agent 运行
SELECT run_id, run_status, model, max_steps, step_count,
       error_message, create_time, update_time
FROM expert_agent_run
ORDER BY create_time DESC
LIMIT 10;

SELECT s.run_id, s.step_no, s.tool_name, s.step_status,
       s.latency_ms, s.create_time
FROM expert_agent_step s
ORDER BY s.run_id DESC, s.step_no
LIMIT 50;

-- 10. 分角度认证状态
SELECT a.category_code, a.angle_name, a.required_flag,
       m.material_id, m.version_no, m.file_name,
       m.job_status, m.review_status, m.review_comment,
       (
           SELECT COUNT(*) FROM expert_material am
           WHERE am.user_id = @uid
             AND am.material_category = a.category_code
             AND am.review_status = 'APPROVED'
       ) AS approved_count
FROM expert_certification_angle a
LEFT JOIN expert_material m ON m.material_id = (
    SELECT MAX(m2.material_id)
    FROM expert_material m2
    WHERE m2.user_id = @uid AND m2.material_category = a.category_code
)
WHERE a.enabled = '0'
ORDER BY a.sort_no;

-- 8. 审核日志
SELECT log_id, material_id, action, reviewer_name,
       previous_status, current_status, comment, create_time
FROM expert_review_log
WHERE user_id = @uid
ORDER BY log_id DESC;

-- 9. 接单状态变更记录
SELECT log_id, previous_status, current_status, reason,
       credit_related, operator_name, create_time
FROM expert_status_log
WHERE user_id = @uid
ORDER BY log_id DESC;

-- 10. 正式专家库
SELECT approved_id, user_id, real_name, organization, research_field,
       industry_field, service_region, service_status,
       source_material_id, graph_version, approved_by, approved_time
FROM expert_approved
WHERE user_id = @uid;

-- 11. 材料版本重复检查，期望无结果
SELECT user_id, material_category, version_no, COUNT(*) AS duplicate_count
FROM expert_material
GROUP BY user_id, material_category, version_no
HAVING COUNT(*) > 1;

-- 12. 超时 Agent 任务检查
SELECT material_id, user_id, job_status, processing_token,
       processing_started_at, update_time
FROM expert_material
WHERE job_status = 'PROCESSING'
  AND processing_started_at < DATE_SUB(NOW(), INTERVAL 30 MINUTE);

-- 13. 待补偿的 MinIO 删除任务
SELECT cleanup_id, object_uri, task_status, attempt_count,
       next_retry_time, last_error
FROM expert_object_cleanup
WHERE task_status = 'PENDING'
ORDER BY cleanup_id;

-- 14. 图谱驱动画像追溯，正式画像应全部引用ACCEPTED图谱声明
SELECT s.dimension_code, s.profile_kind, s.inference_method,
       s.graph_path_count, s.graph_relation_ids, s.graph_evidence_ids,
       s.profile_rule_version
FROM expert_graph_score s
WHERE s.user_id = @uid
  AND s.graph_status = 'APPROVED'
ORDER BY s.dimension_code;

SELECT r.review_status, COUNT(*) AS relation_count
FROM kg_relation r
JOIN kg_evidence e ON e.relation_id = r.relation_id
JOIN kg_entity a ON a.entity_id = r.target_entity_id
WHERE a.entity_type = 'CapabilityAssessment'
  AND e.material_id IN (
      SELECT material_id FROM expert_material WHERE user_id = @uid
  )
GROUP BY r.review_status;

-- 15. 最近匹配任务的必需能力
SELECT t.canonical_name AS task_name, c.canonical_name AS required_capability,
       r.review_status, t.create_time
FROM kg_entity t
JOIN kg_relation r ON r.source_entity_id = t.entity_id
JOIN kg_entity c ON c.entity_id = r.target_entity_id
WHERE t.entity_type = 'Task'
  AND r.relation_type = 'REQUIRES_CAPABILITY'
ORDER BY t.create_time DESC, c.canonical_name
LIMIT 50;
