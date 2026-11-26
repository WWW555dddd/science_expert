-- 将“重点产业覆盖”六维调整为“领域迁移与知识适配”六维。
-- 保留其余24个画像维度；旧六维分数先归档，再由新材料重新分析生成。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_graph_score_adaptation_archive
LIKE expert_graph_score;

CREATE TABLE IF NOT EXISTS expert_graph_dimension_evidence_adaptation_archive
LIKE expert_graph_dimension_evidence;

INSERT IGNORE INTO expert_graph_score_adaptation_archive
SELECT *
FROM expert_graph_score
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

INSERT IGNORE INTO expert_graph_dimension_evidence_adaptation_archive
SELECT *
FROM expert_graph_dimension_evidence
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

DELETE FROM kg_dimension_capability_map
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

DELETE FROM expert_graph_dimension_evidence
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

DELETE FROM expert_graph_score
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

DELETE FROM expert_dimension_definition
WHERE dimension_code IN (
    'IND_ELECTRONICS', 'IND_NONFERROUS', 'IND_EQUIPMENT',
    'IND_NEW_ENERGY', 'IND_BIOMED', 'IND_CHEM_MATERIAL'
);

INSERT IGNORE INTO expert_dimension_definition
    (dimension_code, group_code, group_name, dimension_name,
     weight, enabled, sort_no, description)
VALUES
('ADAPT_DOMAIN_DEPTH', 'ADAPTATION', '领域迁移与知识适配', '专业领域深度', 1.20, '0', 10,
 '在核心专业方向形成持续积累并具备深入解决专业问题的能力'),
('ADAPT_EXPERIENCE_BREADTH', 'ADAPTATION', '领域迁移与知识适配', '行业经验广度', 1.10, '0', 11,
 '跨行业、跨项目类型开展技术服务的经历广度'),
('ADAPT_CROSS_DOMAIN', 'ADAPTATION', '领域迁移与知识适配', '跨领域迁移', 1.30, '0', 12,
 '将已有知识和方法迁移到新产业、新技术和新风险场景的能力'),
('ADAPT_SCENE_UNDERSTANDING', 'ADAPTATION', '领域迁移与知识适配', '新场景理解', 1.20, '0', 13,
 '快速理解陌生保险标的、业务场景、客户需求和风险边界的能力'),
('ADAPT_METHOD_REUSE', 'ADAPTATION', '领域迁移与知识适配', '方法工具复用', 1.10, '0', 14,
 '将检测、仿真、建模、工程分析或评估方法复用于新问题的能力'),
('ADAPT_KNOWLEDGE_SYNTHESIS', 'ADAPTATION', '领域迁移与知识适配', '知识整合能力', 1.00, '0', 15,
 '整合跨材料、跨专业和跨项目信息形成可执行技术判断的能力');

INSERT IGNORE INTO kg_dimension_capability_map
    (dimension_code, capability_code, capability_name, ontology_version, create_time)
SELECT dimension_code,
       CONCAT('CAP_', dimension_code),
       dimension_name,
       'expert-domain-v1',
       NOW()
FROM expert_dimension_definition
WHERE group_code = 'ADAPTATION';
