-- 材料审核AI结果可编辑字段，保留AI原始值并记录审核修订（MySQL 8+，可重复执行）。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_ai_review_column_if_missing//
CREATE PROCEDURE add_ai_review_column_if_missing(
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

CALL add_ai_review_column_if_missing(
    'expert_field_mapping',
    'reviewed_value',
    "VARCHAR(1000) DEFAULT NULL COMMENT '审核人修订后的提取值' AFTER extracted_value"
);
CALL add_ai_review_column_if_missing(
    'expert_field_mapping',
    'reviewed_evidence',
    "VARCHAR(1000) DEFAULT NULL COMMENT '审核人修订后的证据说明' AFTER evidence"
);
CALL add_ai_review_column_if_missing(
    'expert_field_mapping',
    'reviewed_confidence',
    "DECIMAL(5,2) DEFAULT NULL COMMENT '审核人修订后的置信度' AFTER confidence"
);
CALL add_ai_review_column_if_missing(
    'expert_dimension_assessment',
    'reviewed_score',
    "DECIMAL(5,2) DEFAULT NULL COMMENT '审核人修订后的维度分' AFTER score"
);
CALL add_ai_review_column_if_missing(
    'expert_dimension_assessment',
    'reviewed_evidence',
    "VARCHAR(1000) DEFAULT NULL COMMENT '审核人修订后的维度证据' AFTER evidence"
);
CALL add_ai_review_column_if_missing(
    'expert_dimension_assessment',
    'review_comment',
    "VARCHAR(500) DEFAULT NULL COMMENT 'AI结果审核意见' AFTER review_status"
);
CALL add_ai_review_column_if_missing(
    'expert_dimension_assessment',
    'reviewer_name',
    "VARCHAR(64) DEFAULT NULL COMMENT 'AI结果审核人' AFTER review_comment"
);
CALL add_ai_review_column_if_missing(
    'expert_dimension_assessment',
    'review_time',
    "DATETIME DEFAULT NULL COMMENT 'AI结果审核时间' AFTER reviewer_name"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_excerpt',
    "VARCHAR(1000) DEFAULT NULL COMMENT '审核人修订后的关系证据' AFTER excerpt"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_source_name',
    "VARCHAR(255) DEFAULT NULL COMMENT '审核人修订后的源节点名称' AFTER reviewed_excerpt"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_source_type',
    "VARCHAR(64) DEFAULT NULL COMMENT '审核人修订后的源节点类型' AFTER reviewed_source_name"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_relation_type',
    "VARCHAR(64) DEFAULT NULL COMMENT '审核人修订后的关系类型' AFTER reviewed_source_type"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_target_name',
    "VARCHAR(255) DEFAULT NULL COMMENT '审核人修订后的目标节点名称' AFTER reviewed_relation_type"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewed_target_type',
    "VARCHAR(64) DEFAULT NULL COMMENT '审核人修订后的目标节点类型' AFTER reviewed_target_name"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'review_comment',
    "VARCHAR(500) DEFAULT NULL COMMENT '关系证据审核意见' AFTER review_status"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'reviewer_name',
    "VARCHAR(64) DEFAULT NULL COMMENT '关系证据审核人' AFTER review_comment"
);
CALL add_ai_review_column_if_missing(
    'kg_evidence',
    'review_time',
    "DATETIME DEFAULT NULL COMMENT '关系证据审核时间' AFTER reviewer_name"
);

DROP PROCEDURE add_ai_review_column_if_missing;
