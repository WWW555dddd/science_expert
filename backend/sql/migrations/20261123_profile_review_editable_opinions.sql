-- 保存审核人对AI画像意见的修订版本，同时保留AI原始摘要和理由（MySQL 8+，可重复执行）。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_profile_review_opinion_column_if_missing//
CREATE PROCEDURE add_profile_review_opinion_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN definition_value VARCHAR(1000)
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

CALL add_profile_review_opinion_column_if_missing(
    'expert_profile_update_suggestion',
    'reviewed_summary',
    "VARCHAR(2000) DEFAULT NULL COMMENT '审核人修订后的建议摘要' AFTER summary"
);
CALL add_profile_review_opinion_column_if_missing(
    'expert_profile_update_suggestion_item',
    'reviewed_reason',
    "VARCHAR(1000) DEFAULT NULL COMMENT '审核人修订后的维度理由' AFTER reason"
);

DROP PROCEDURE add_profile_review_opinion_column_if_missing;
