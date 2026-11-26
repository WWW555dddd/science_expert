-- 实时服务证据画像分析 Agent 审计字段（MySQL 8+，可重复执行）。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_profile_suggestion_column_if_missing//
CREATE PROCEDURE add_profile_suggestion_column_if_missing(
    IN column_name_value VARCHAR(64),
    IN definition_value VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'expert_profile_update_suggestion'
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE expert_profile_update_suggestion ADD COLUMN `',
            column_name_value, '` ', definition_value
        );
        PREPARE ddl_statement FROM @ddl;
        EXECUTE ddl_statement;
        DEALLOCATE PREPARE ddl_statement;
    END IF;
END//
DELIMITER ;

CALL add_profile_suggestion_column_if_missing('generation_mode',
    "VARCHAR(32) NOT NULL DEFAULT 'AGENT' COMMENT 'AGENT/RULE_FALLBACK/SEEDED' AFTER confidence");
CALL add_profile_suggestion_column_if_missing('prompt_version',
    "VARCHAR(64) DEFAULT NULL COMMENT '画像分析Prompt版本' AFTER generation_mode");
CALL add_profile_suggestion_column_if_missing('model_name',
    "VARCHAR(128) DEFAULT NULL COMMENT '画像分析模型名称' AFTER prompt_version");
CALL add_profile_suggestion_column_if_missing('agent_output_json',
    "LONGTEXT DEFAULT NULL COMMENT 'Agent结构化输出审计' AFTER model_name");

DROP PROCEDURE add_profile_suggestion_column_if_missing;

UPDATE expert_profile_update_suggestion
SET generation_mode = 'SEEDED'
WHERE generation_mode IN ('AGENT', 'RULE_FALLBACK')
  AND prompt_version IS NULL
  AND model_name IS NULL
  AND agent_output_json IS NULL;
