-- 将专家认证的“其他材料”调整为“利益冲突说明”，
-- 并用四个“利益冲突与独立性”维度替换重复度较高的画像维度。
-- 总维度数保持 30；旧画像分数和逐材料证据只会归档，不删除专家业务数据。
USE science_expert;

INSERT INTO expert_certification_angle
    (category_code, angle_name, required_flag, sort_no, enabled, description)
VALUES
    ('OTHER', '利益冲突说明', '1', 5, '0',
     '说明本人及近亲属与客户、项目参与方、保险标的之间的利益关系、关联关系和回避安排，审核通过后方可完成整体认证')
ON DUPLICATE KEY UPDATE
    angle_name = VALUES(angle_name),
    required_flag = VALUES(required_flag),
    sort_no = VALUES(sort_no),
    enabled = VALUES(enabled),
    description = VALUES(description);

CREATE TABLE IF NOT EXISTS expert_graph_score_conflict_archive
LIKE expert_graph_score;

CREATE TABLE IF NOT EXISTS expert_graph_dimension_evidence_conflict_archive
LIKE expert_graph_dimension_evidence;

INSERT IGNORE INTO expert_graph_score_conflict_archive
SELECT *
FROM expert_graph_score
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

INSERT IGNORE INTO expert_graph_dimension_evidence_conflict_archive
SELECT *
FROM expert_graph_dimension_evidence
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

DELETE FROM kg_dimension_capability_map
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

DELETE FROM expert_graph_dimension_evidence
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

DELETE FROM expert_graph_score
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

DELETE FROM expert_dimension_definition
WHERE dimension_code IN (
    'ADAPT_EXPERIENCE_BREADTH',
    'ADAPT_SCENE_UNDERSTANDING',
    'ADAPT_KNOWLEDGE_SYNTHESIS',
    'PERF_COLLABORATION'
);

INSERT IGNORE INTO expert_dimension_definition
    (dimension_code, group_code, group_name, dimension_name,
     weight, enabled, sort_no, description)
VALUES
('CONFLICT_DISCLOSURE_COMPLETE', 'CONFLICT', '利益冲突与独立性', '利益关系披露', 1.50, '0', 27,
 '完整披露本人及近亲属与客户、项目参与方和保险标的之间的利益关系'),
('CONFLICT_RELATION_TRANSPARENCY', 'CONFLICT', '利益冲突与独立性', '关联关系透明', 1.40, '0', 28,
 '对任职、兼职、顾问、股权和合作项目等关联关系作出清晰且可核验的说明'),
('CONFLICT_COMMERCIAL_INDEPENDENCE', 'CONFLICT', '利益冲突与独立性', '商业利益独立', 1.50, '0', 29,
 '不存在可能影响独立判断的报酬、返佣、推荐收益、股权或其他商业利益'),
('CONFLICT_RECUSAL_MECHANISM', 'CONFLICT', '利益冲突与独立性', '回避机制执行', 1.40, '0', 30,
 '能够主动识别潜在利益冲突并执行回避、补充申报和独立判断要求');

UPDATE expert_dimension_definition SET sort_no = 10 WHERE dimension_code = 'ADAPT_DOMAIN_DEPTH';
UPDATE expert_dimension_definition SET sort_no = 11 WHERE dimension_code = 'ADAPT_CROSS_DOMAIN';
UPDATE expert_dimension_definition SET sort_no = 12 WHERE dimension_code = 'ADAPT_METHOD_REUSE';
UPDATE expert_dimension_definition SET sort_no = 13 WHERE dimension_code = 'SCENE_PRE_SURVEY';
UPDATE expert_dimension_definition SET sort_no = 14 WHERE dimension_code = 'SCENE_RISK_REDUCTION';
UPDATE expert_dimension_definition SET sort_no = 15 WHERE dimension_code = 'SCENE_CLAIM_INSPECTION';
UPDATE expert_dimension_definition SET sort_no = 16 WHERE dimension_code = 'SCENE_TECH_APPRAISAL';
UPDATE expert_dimension_definition SET sort_no = 17 WHERE dimension_code = 'SCENE_TECH_RESCUE';
UPDATE expert_dimension_definition SET sort_no = 18 WHERE dimension_code = 'SERVICE_REGION';
UPDATE expert_dimension_definition SET sort_no = 19 WHERE dimension_code = 'SERVICE_RESPONSE';
UPDATE expert_dimension_definition SET sort_no = 20 WHERE dimension_code = 'SERVICE_MODE';
UPDATE expert_dimension_definition SET sort_no = 21 WHERE dimension_code = 'SERVICE_COMPLIANCE';
UPDATE expert_dimension_definition SET sort_no = 22 WHERE dimension_code = 'SERVICE_COST';
UPDATE expert_dimension_definition SET sort_no = 23 WHERE dimension_code = 'PERF_SIMILAR_PROJECT';
UPDATE expert_dimension_definition SET sort_no = 24 WHERE dimension_code = 'PERF_REPORT_QUALITY';
UPDATE expert_dimension_definition SET sort_no = 25 WHERE dimension_code = 'PERF_COMPLETION_CREDIT';
UPDATE expert_dimension_definition SET sort_no = 26 WHERE dimension_code = 'PERF_KNOWLEDGE_REUSE';

UPDATE expert_profile
SET graph_version = 0,
    update_time = NOW();

UPDATE expert_approved
SET graph_version = 0;
