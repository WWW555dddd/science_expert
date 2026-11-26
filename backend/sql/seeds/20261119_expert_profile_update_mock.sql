-- 为已有模拟服务报告生成一条待审核画像更新建议（MySQL 8+，可重复执行）。
USE science_expert;

SET @mock_evidence_id = (
    SELECT evidence_id
    FROM expert_service_evidence
    WHERE source_event_id = 'MOCK-EVIDENCE-20261119-001'
    LIMIT 1
);
SET @mock_expert_user_id = (
    SELECT expert_user_id
    FROM expert_service_evidence
    WHERE evidence_id = @mock_evidence_id
    LIMIT 1
);

INSERT IGNORE INTO expert_profile_update_suggestion
    (evidence_id, user_id, status, summary, confidence, version,
     create_time, update_time)
SELECT
    @mock_evidence_id, @mock_expert_user_id, 'PENDING',
    '模拟服务报告呈现正向质量信号，建议小幅上调历史报告质量和同类项目经验维度；批准前不修改生效画像。',
    0.72, 0, NOW(), NOW()
FROM DUAL
WHERE @mock_evidence_id IS NOT NULL
  AND @mock_expert_user_id IS NOT NULL;

SET @mock_suggestion_id = (
    SELECT suggestion_id
    FROM expert_profile_update_suggestion
    WHERE evidence_id = @mock_evidence_id
    LIMIT 1
);

INSERT IGNORE INTO expert_profile_update_suggestion_item
    (suggestion_id, dimension_code, dimension_name, before_score,
     suggested_score, reason, evidence, confidence, create_time)
SELECT
    @mock_suggestion_id, 'PERF_REPORT_QUALITY', '历史报告质量', 80, 83,
    'SERVICE_REPORT 证据显示报告质量良好，建议小幅上调。',
    '模拟科技保险专家服务报告：服务按期完成，报告结构完整。',
    0.72, NOW()
FROM DUAL
WHERE @mock_suggestion_id IS NOT NULL;

UPDATE expert_service_evidence
SET processing_status = 'SUGGESTED', update_time = NOW()
WHERE evidence_id = @mock_evidence_id
  AND processing_status = 'RECEIVED';

INSERT IGNORE INTO expert_profile_update_suggestion_item
    (suggestion_id, dimension_code, dimension_name, before_score,
     suggested_score, reason, evidence, confidence, create_time)
SELECT
    @mock_suggestion_id, 'PERF_SIMILAR_PROJECT', '同类项目经验', 70, 73,
    'SERVICE_REPORT 证据补充了同类任务完成事实，建议小幅上调。',
    '模拟科技保险专家服务报告：服务按期完成，报告结构完整。',
    0.72, NOW()
FROM DUAL
WHERE @mock_suggestion_id IS NOT NULL;
