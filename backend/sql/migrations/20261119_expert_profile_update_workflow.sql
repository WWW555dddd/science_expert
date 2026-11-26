-- 服务证据驱动的画像更新建议与人工审核（MySQL 8+，可重复执行）。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_profile_update_suggestion (
    suggestion_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '画像更新建议ID',
    evidence_id BIGINT NOT NULL COMMENT '来源服务证据ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/APPROVED/REJECTED',
    summary VARCHAR(2000) NOT NULL COMMENT 'Agent更新建议摘要',
    confidence DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '建议整体置信度',
    reviewer_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    reviewer_name VARCHAR(64) DEFAULT NULL COMMENT '审核人',
    review_comment VARCHAR(2000) DEFAULT NULL COMMENT '审核意见',
    release_id BIGINT DEFAULT NULL COMMENT '批准后生成的画像版本ID',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    resolved_time DATETIME DEFAULT NULL,
    PRIMARY KEY (suggestion_id),
    UNIQUE KEY uk_suggestion_evidence (evidence_id),
    KEY idx_suggestion_expert (user_id, status, create_time),
    CONSTRAINT fk_suggestion_evidence FOREIGN KEY (evidence_id)
        REFERENCES expert_service_evidence(evidence_id) ON DELETE CASCADE,
    CONSTRAINT fk_suggestion_expert FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务事实驱动的画像更新建议';

CREATE TABLE IF NOT EXISTS expert_profile_update_suggestion_item (
    item_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '建议明细ID',
    suggestion_id BIGINT NOT NULL COMMENT '画像更新建议ID',
    dimension_code VARCHAR(64) NOT NULL COMMENT '画像维度编码',
    dimension_name VARCHAR(128) NOT NULL COMMENT '画像维度名称',
    before_score DECIMAL(5,2) NOT NULL COMMENT '当前评分',
    suggested_score DECIMAL(5,2) NOT NULL COMMENT '建议评分',
    approved_score DECIMAL(5,2) DEFAULT NULL COMMENT '人工批准评分',
    reason VARCHAR(1000) NOT NULL COMMENT '更新理由',
    evidence VARCHAR(2000) NOT NULL COMMENT '证据摘要',
    confidence DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '明细置信度',
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (item_id),
    KEY idx_suggestion_item_suggestion (suggestion_id),
    CONSTRAINT fk_suggestion_item_suggestion FOREIGN KEY (suggestion_id)
        REFERENCES expert_profile_update_suggestion(suggestion_id) ON DELETE CASCADE,
    CONSTRAINT fk_suggestion_item_dimension FOREIGN KEY (dimension_code)
        REFERENCES expert_dimension_definition(dimension_code) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='画像更新建议明细';

SET @suggestion_item_unique_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'expert_profile_update_suggestion_item'
      AND index_name = 'uk_suggestion_item_dimension'
);
SET @suggestion_item_unique_sql = IF(
    @suggestion_item_unique_exists = 0,
    'ALTER TABLE expert_profile_update_suggestion_item ADD UNIQUE KEY uk_suggestion_item_dimension (suggestion_id, dimension_code)',
    'SELECT 1'
);
PREPARE suggestion_item_unique_stmt FROM @suggestion_item_unique_sql;
EXECUTE suggestion_item_unique_stmt;
DEALLOCATE PREPARE suggestion_item_unique_stmt;

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
     create_time, update_by, update_time, remark)
VALUES
    (4120, '画像更新审核', 4100, 2, 'profile-update', 'expert/profile-update/index',
     '', 'ExpertProfileUpdateReview', 1, 0, 'C', '0', '0',
     'expert:profile-update:review', 'edit', 'admin', NOW(), '', NULL,
     '审核服务事实驱动的画像更新建议');

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
VALUES (1, 4120);

UPDATE expert_service_evidence
SET processing_status = 'SUGGESTED'
WHERE processing_status = 'RECEIVED'
  AND evidence_id IN (
      SELECT evidence_id FROM expert_profile_update_suggestion
  );
