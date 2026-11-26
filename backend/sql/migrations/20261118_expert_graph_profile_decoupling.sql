-- 将30维画像与知识图谱解耦。
-- 画像材料评分迁移到 expert_dimension_assessment；
-- 图谱中心改为 Industry，并通过 HAS_EXPERT 辐射到专家和关键事实节点。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_dimension_assessment (
    assessment_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    dimension_code VARCHAR(64) NOT NULL,
    score DECIMAL(5,2) NOT NULL,
    confidence DECIMAL(5,2) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    rule_version VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (assessment_id),
    UNIQUE KEY uk_dimension_assessment_material (material_id, dimension_code),
    KEY idx_dimension_assessment_user (user_id, review_status, dimension_code),
    CONSTRAINT fk_dimension_assessment_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_dimension_assessment_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE CASCADE,
    CONSTRAINT fk_dimension_assessment_definition FOREIGN KEY (dimension_code)
        REFERENCES expert_dimension_definition(dimension_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='30维画像逐材料评估，独立于知识图谱';

CREATE TABLE IF NOT EXISTS kg_entity_profile_decoupling_archive
LIKE kg_entity;

CREATE TABLE IF NOT EXISTS kg_relation_profile_decoupling_archive
LIKE kg_relation;

CREATE TABLE IF NOT EXISTS kg_evidence_profile_decoupling_archive
LIKE kg_evidence;

INSERT IGNORE INTO kg_entity_profile_decoupling_archive
SELECT *
FROM kg_entity
WHERE entity_type IN ('CapabilityAssessment', 'Certification', 'Material')
   OR (
       entity_type = 'Capability'
       AND JSON_EXTRACT(properties_json, '$.dimensionCode') IS NOT NULL
   );

INSERT IGNORE INTO kg_relation_profile_decoupling_archive
SELECT r.*
FROM kg_relation r
WHERE r.source_entity_id IN (
        SELECT entity_id FROM kg_entity_profile_decoupling_archive
      )
   OR r.target_entity_id IN (
        SELECT entity_id FROM kg_entity_profile_decoupling_archive
      )
   OR r.relation_type = 'WORKS_IN_INDUSTRY';

INSERT IGNORE INTO kg_evidence_profile_decoupling_archive
SELECT ev.*
FROM kg_evidence ev
WHERE ev.entity_id IN (
        SELECT entity_id FROM kg_entity_profile_decoupling_archive
      )
   OR ev.relation_id IN (
        SELECT relation_id FROM kg_relation_profile_decoupling_archive
      );

INSERT IGNORE INTO expert_dimension_assessment
    (user_id, material_id, dimension_code, score, confidence, evidence,
     review_status, rule_version, create_time, update_time)
SELECT CAST(SUBSTRING_INDEX(expert.external_key, ':', -1) AS UNSIGNED),
       ev.material_id,
       JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.dimensionCode')),
       CAST(JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.score'))
            AS DECIMAL(5,2)),
       CAST(JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.confidence'))
            AS DECIMAL(5,2)),
       LEFT(COALESCE(ev.excerpt, ''), 1000),
       CASE WHEN r.review_status = 'ACCEPTED' THEN 'ACCEPTED'
            WHEN r.review_status = 'REJECTED' THEN 'REJECTED'
            ELSE 'PENDING' END,
       'profile-assessment-v3',
       assessment.create_time,
       assessment.update_time
FROM kg_entity_profile_decoupling_archive assessment
JOIN kg_relation_profile_decoupling_archive r
  ON r.target_entity_id = assessment.entity_id
 AND r.relation_type = 'ASSESSED_AS'
JOIN kg_entity expert
  ON expert.entity_id = r.source_entity_id
 AND expert.entity_type = 'Expert'
JOIN kg_evidence_profile_decoupling_archive ev
  ON ev.relation_id = r.relation_id
WHERE assessment.entity_type = 'CapabilityAssessment'
  AND JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.dimensionCode')) IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.score')) IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(assessment.properties_json, '$.confidence')) IS NOT NULL
  AND ev.material_id IS NOT NULL;

DELETE ev
FROM kg_evidence ev
WHERE ev.entity_id IN (
        SELECT entity_id FROM kg_entity_profile_decoupling_archive
      )
   OR ev.relation_id IN (
        SELECT relation_id FROM kg_relation_profile_decoupling_archive
      );

DELETE r
FROM kg_relation r
WHERE r.relation_id IN (
        SELECT relation_id FROM kg_relation_profile_decoupling_archive
      );

DELETE FROM kg_entity
WHERE entity_id IN (
    SELECT entity_id FROM kg_entity_profile_decoupling_archive
);

DELETE FROM kg_dimension_capability_map;

INSERT IGNORE INTO kg_ontology_version
    (version_code, display_name, resource_path, status, published_at, create_time)
VALUES
    ('expert-domain-v3', '科技保险专家检索图谱 V3',
     'classpath:knowledge-graph/ontology-v3.json', 'ACTIVE', NOW(), NOW());

UPDATE kg_ontology_version
SET status = 'RETIRED'
WHERE version_code <> 'expert-domain-v3';

INSERT IGNORE INTO kg_relation
    (source_entity_id, relation_type, target_entity_id, confidence,
     valid_from, valid_to, review_status, ontology_version, source_kind,
     properties_json, create_time, update_time)
SELECT r.target_entity_id,
       'HAS_EXPERT',
       r.source_entity_id,
       r.confidence,
       r.valid_from,
       r.valid_to,
       r.review_status,
       'expert-domain-v3',
       r.source_kind,
       r.properties_json,
       r.create_time,
       r.update_time
FROM kg_relation_profile_decoupling_archive r
WHERE r.relation_type = 'WORKS_IN_INDUSTRY'
  AND r.valid_to IS NULL;

UPDATE kg_relation
SET valid_to = NOW(),
    update_time = NOW()
WHERE relation_type = 'WORKS_IN_INDUSTRY'
  AND valid_to IS NULL;

UPDATE kg_entity
SET ontology_version = 'expert-domain-v3',
    update_time = NOW()
WHERE review_status <> 'REJECTED';

UPDATE kg_relation
SET ontology_version = 'expert-domain-v3',
    update_time = NOW()
WHERE review_status <> 'REJECTED';

UPDATE expert_graph_score
SET profile_kind = 'EVIDENCE_SCORE',
    inference_method = 'DIMENSION_ASSESSMENT_AGGREGATE',
    graph_relation_ids = NULL,
    graph_entity_ids = NULL,
    graph_evidence_ids = NULL,
    graph_path_count = 0,
    profile_rule_version = 'profile-assessment-v3'
WHERE profile_kind = 'GRAPH_PROFILE';

UPDATE expert_graph_dimension_evidence
SET inference_method = 'DIMENSION_ASSESSMENT',
    graph_relation_id = NULL,
    graph_entity_id = NULL,
    graph_evidence_id = NULL,
    profile_rule_version = 'profile-assessment-v3'
WHERE graph_relation_id IS NOT NULL
   OR graph_entity_id IS NOT NULL
   OR graph_evidence_id IS NOT NULL;

UPDATE expert_profile
SET graph_version = 0,
    update_time = NOW();

INSERT INTO kg_change_outbox
    (event_id, event_type, aggregate_type, aggregate_id, payload_json,
     event_status, attempt_count, available_at, create_time, update_time)
SELECT UUID(), 'EXPERT_PROJECTED', 'EXPERT', p.user_id,
       JSON_OBJECT('userId', p.user_id),
       'PENDING', 0, NOW(), NOW(), NOW()
FROM expert_profile p;
