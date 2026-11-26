-- 专家接单后的正式安排确认与占用释放（MySQL 8+，可重复执行）。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_task_acceptance_column_if_missing//
CREATE PROCEDURE add_task_acceptance_column_if_missing(
    IN column_name_value VARCHAR(64),
    IN definition_value VARCHAR(1000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'expert_task_conflict_confirmation'
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE expert_task_conflict_confirmation ADD COLUMN `',
            column_name_value, '` ', definition_value
        );
        PREPARE ddl_statement FROM @ddl;
        EXECUTE ddl_statement;
        DEALLOCATE PREPARE ddl_statement;
    END IF;
END//
DELIMITER ;

CALL add_task_acceptance_column_if_missing('arrangement_status',
    "VARCHAR(32) NOT NULL DEFAULT 'NOT_CONFIRMED' COMMENT 'NOT_CONFIRMED/CONFIRMED/CANCELLED' AFTER status");
CALL add_task_acceptance_column_if_missing('arranged_by',
    "VARCHAR(64) DEFAULT NULL COMMENT '正式安排确认人' AFTER arrangement_status");
CALL add_task_acceptance_column_if_missing('arranged_at',
    "DATETIME DEFAULT NULL COMMENT '正式安排确认时间' AFTER arranged_by");
CALL add_task_acceptance_column_if_missing('arrangement_source',
    "VARCHAR(128) DEFAULT NULL COMMENT '正式安排来源系统' AFTER arranged_at");
CALL add_task_acceptance_column_if_missing('arrangement_version',
    "VARCHAR(128) DEFAULT NULL COMMENT '正式安排来源版本' AFTER arrangement_source");

DROP PROCEDURE add_task_acceptance_column_if_missing;

UPDATE expert_task_conflict_confirmation
SET arrangement_status = 'NOT_CONFIRMED'
WHERE arrangement_status IS NULL OR arrangement_status = '';
