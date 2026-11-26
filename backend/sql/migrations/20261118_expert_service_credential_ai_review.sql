-- 服务经历材料、资质有效期/提醒与逐条 AI 结果确认。
-- MySQL 8+；新增字段使用存储过程实现可重复执行。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_expert_column_if_missing//
CREATE PROCEDURE add_expert_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN definition_value VARCHAR(500)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', table_name_value, '` ADD COLUMN `',
            column_name_value, '` ', definition_value
        );
        PREPARE ddl_statement FROM @ddl;
        EXECUTE ddl_statement;
        DEALLOCATE PREPARE ddl_statement;
    END IF;
END//
DELIMITER ;

CALL add_expert_column_if_missing('expert_certification_angle', 'validity_required',
    "CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否登记有效期：1是 0否' AFTER enabled");
CALL add_expert_column_if_missing('expert_material', 'valid_from',
    "DATE DEFAULT NULL COMMENT '资质有效期开始' AFTER review_comment");
CALL add_expert_column_if_missing('expert_material', 'valid_until',
    "DATE DEFAULT NULL COMMENT '资质有效期结束' AFTER valid_from");
CALL add_expert_column_if_missing('expert_field_mapping', 'review_status',
    "VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'AI结果人工确认状态' AFTER conflict_flag");
CALL add_expert_column_if_missing('expert_field_mapping', 'review_comment',
    "VARCHAR(500) DEFAULT NULL COMMENT 'AI结果确认意见' AFTER review_status");
CALL add_expert_column_if_missing('expert_field_mapping', 'reviewer_name',
    "VARCHAR(64) DEFAULT NULL COMMENT '确认人' AFTER review_comment");
CALL add_expert_column_if_missing('expert_field_mapping', 'review_time',
    "DATETIME DEFAULT NULL COMMENT '确认时间' AFTER reviewer_name");

DROP PROCEDURE add_expert_column_if_missing;

INSERT INTO expert_certification_angle
    (category_code, angle_name, required_flag, sort_no, enabled, validity_required, description)
VALUES
    ('SERVICE_EXPERIENCE', '服务经历', '0', 5, '0', '0',
     '代表性服务项目、服务对象、承担工作、服务时间及成果说明')
ON DUPLICATE KEY UPDATE
    angle_name = VALUES(angle_name),
    sort_no = VALUES(sort_no),
    enabled = VALUES(enabled),
    validity_required = VALUES(validity_required),
    description = VALUES(description);

UPDATE expert_certification_angle
SET sort_no = 6,
    validity_required = '0'
WHERE category_code = 'OTHER';

UPDATE expert_certification_angle
SET validity_required = '1'
WHERE category_code IN ('QUALIFICATION', 'CERTIFICATE');

UPDATE expert_material
SET job_status = 'QUEUED',
    processing_token = NULL,
    processing_started_at = NULL,
    agent_message = '版本已提交审核，等待重新处理',
    update_time = NOW()
WHERE job_status = 'FILES_CHANGED';

CREATE TABLE IF NOT EXISTS expert_qualification_reminder (
    reminder_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '提醒ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    material_id BIGINT NOT NULL COMMENT '资质材料ID',
    material_category VARCHAR(64) NOT NULL COMMENT '材料类别',
    expiry_date DATE NOT NULL COMMENT '到期日期',
    reminder_type VARCHAR(16) NOT NULL COMMENT 'EXPIRING/EXPIRED',
    reminder_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RESOLVED',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (reminder_id),
    UNIQUE KEY uk_expert_qualification_reminder
        (material_id, expiry_date, reminder_type),
    KEY idx_expert_qualification_reminder_user (user_id, reminder_status),
    CONSTRAINT fk_expert_qualification_reminder_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家资质到期提醒';
