-- 任务占用只读台账、风险预警审核和任务级冲突确认（MySQL 8+，可重复执行）。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_risk_review (
    risk_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '风险预警ID',
    evidence_id BIGINT DEFAULT NULL COMMENT '来源服务证据ID',
    source_event_id VARCHAR(128) NOT NULL COMMENT '来源事件幂等键',
    expert_user_id BIGINT NOT NULL COMMENT '专家用户ID',
    risk_type VARCHAR(32) NOT NULL COMMENT 'COMPLAINT/LOW_EVALUATION/CREDIT_WARNING',
    severity VARCHAR(16) NOT NULL COMMENT 'LOW/MEDIUM/HIGH',
    title VARCHAR(255) NOT NULL COMMENT '风险标题',
    summary VARCHAR(2000) DEFAULT NULL COMMENT '风险摘要',
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACKNOWLEDGED/DISMISSED',
    reviewer_id BIGINT DEFAULT NULL,
    reviewer_name VARCHAR(64) DEFAULT NULL,
    review_comment VARCHAR(2000) DEFAULT NULL,
    source_system VARCHAR(128) NOT NULL,
    source_kind VARCHAR(32) NOT NULL DEFAULT 'EXTERNAL',
    occurred_at DATETIME DEFAULT NULL,
    version INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    resolved_time DATETIME DEFAULT NULL,
    PRIMARY KEY (risk_id),
    UNIQUE KEY uk_risk_source_type (source_event_id, risk_type),
    KEY idx_risk_expert_status (expert_user_id, status, create_time),
    CONSTRAINT fk_risk_evidence FOREIGN KEY (evidence_id)
        REFERENCES expert_service_evidence(evidence_id) ON DELETE CASCADE,
    CONSTRAINT fk_risk_expert FOREIGN KEY (expert_user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投诉低评价和信用预警审核';

CREATE TABLE IF NOT EXISTS expert_task_conflict_confirmation (
    confirmation_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务冲突确认ID',
    external_task_id VARCHAR(128) NOT NULL COMMENT '外部任务号',
    expert_user_id BIGINT NOT NULL COMMENT '专家用户ID',
    service_start DATETIME NOT NULL,
    service_end DATETIME NOT NULL,
    service_mode VARCHAR(16) DEFAULT NULL,
    service_region VARCHAR(128) DEFAULT NULL,
    industry VARCHAR(128) DEFAULT NULL,
    conflict_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/NONE/POTENTIAL/CONFIRMED',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/CONFIRMED/REJECTED',
    statement VARCHAR(2000) DEFAULT NULL COMMENT '专家冲突确认说明',
    requested_by VARCHAR(64) DEFAULT NULL,
    request_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME DEFAULT NULL,
    source_system VARCHAR(128) NOT NULL DEFAULT 'topic2-matching',
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (confirmation_id),
    UNIQUE KEY uk_task_conflict_expert_time
        (external_task_id, expert_user_id, service_start, service_end),
    KEY idx_task_conflict_expert_status (expert_user_id, status, request_time),
    KEY idx_task_conflict_task_status (external_task_id, status),
    CONSTRAINT fk_task_conflict_expert FOREIGN KEY (expert_user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务级利益冲突确认';

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
     create_time, update_by, update_time, remark)
VALUES
    (3060, '任务冲突确认', 3000, 6, 'task-conflict', 'expert/task-conflict/index',
     '', 'ExpertTaskConflict', 1, 0, 'C', '0', '0',
     'expert:task-conflict:view', 'warning', 'admin', NOW(), '', NULL,
     '专家确认具体任务的利益冲突'),
    (3061, '提交冲突确认', 3060, 1, '', NULL, '', '', 1, 0, 'F', '0', '0',
     'expert:task-conflict:confirm', '#', 'admin', NOW(), '', NULL, ''),
    (4130, '风险预警审核', 4100, 3, 'risk-review', 'expert/risk-review/index',
     '', 'ExpertRiskReview', 1, 0, 'C', '0', '0',
     'expert:risk-review:review', 'message', 'admin', NOW(), '', NULL,
     '投诉、低评价和信用预警审核');

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
VALUES
    (1001, 3060), (1001, 3061),
    (1, 4130);
