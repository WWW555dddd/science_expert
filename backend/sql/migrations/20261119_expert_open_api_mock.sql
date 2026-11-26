-- 专家业务开放接口、业务快照与模拟数据支撑（MySQL 8+，可重复执行）。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_business_context_snapshot (
    context_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '业务快照ID',
    expert_user_id BIGINT NOT NULL COMMENT '专家用户ID',
    external_task_id VARCHAR(128) DEFAULT NULL COMMENT '外部任务号',
    task_type VARCHAR(128) DEFAULT NULL COMMENT '外部任务类型',
    service_start DATETIME NOT NULL COMMENT '业务核验开始时间',
    service_end DATETIME NOT NULL COMMENT '业务核验结束时间',
    service_mode VARCHAR(16) DEFAULT NULL COMMENT '服务方式',
    service_region VARCHAR(128) DEFAULT NULL COMMENT '服务区域',
    industry VARCHAR(128) DEFAULT NULL COMMENT '产业方向',
    occupancy_status VARCHAR(16) NOT NULL COMMENT 'FREE/OCCUPIED/BLOCKED',
    conflict_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT 'NONE/POTENTIAL/CONFIRMED/UNKNOWN',
    source_system VARCHAR(128) NOT NULL COMMENT '来源系统',
    source_kind VARCHAR(32) NOT NULL DEFAULT 'MOCK'
        COMMENT 'EXTERNAL/MANUAL_IMPORT/MOCK',
    source_version VARCHAR(128) NOT NULL COMMENT '来源版本或幂等键',
    verified_at DATETIME DEFAULT NULL COMMENT '业务源核验时间',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (context_id),
    UNIQUE KEY uk_business_context_source
        (source_system, source_version, expert_user_id),
    KEY idx_business_context_expert_time
        (expert_user_id, service_start, service_end),
    CONSTRAINT fk_business_context_expert FOREIGN KEY (expert_user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部业务上下文只读快照';

CREATE TABLE IF NOT EXISTS expert_service_evidence (
    evidence_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    source_event_id VARCHAR(128) NOT NULL COMMENT '来源幂等事件ID',
    expert_user_id BIGINT NOT NULL COMMENT '专家用户ID',
    evidence_type VARCHAR(32) NOT NULL COMMENT 'SERVICE_REPORT/EVALUATION/CREDIT',
    external_task_id VARCHAR(128) DEFAULT NULL COMMENT '外部任务号',
    source_system VARCHAR(128) NOT NULL COMMENT '来源系统',
    source_kind VARCHAR(32) NOT NULL DEFAULT 'MANUAL_IMPORT'
        COMMENT 'EXTERNAL/MANUAL_IMPORT/MOCK',
    occurred_at DATETIME NOT NULL COMMENT '业务发生时间',
    title VARCHAR(255) DEFAULT NULL COMMENT '证据标题',
    summary VARCHAR(2000) DEFAULT NULL COMMENT '证据摘要',
    raw_payload MEDIUMTEXT DEFAULT NULL COMMENT '原始载荷',
    source_digest VARCHAR(128) DEFAULT NULL COMMENT '来源或载荷摘要',
    processing_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED'
        COMMENT '处理状态，当前仅接收不入画像',
    received_by VARCHAR(64) DEFAULT NULL COMMENT '接收操作人',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_service_evidence_source_event (source_event_id),
    KEY idx_service_evidence_expert_time (expert_user_id, occurred_at),
    KEY idx_service_evidence_type_status (evidence_type, processing_status),
    CONSTRAINT fk_service_evidence_expert FOREIGN KEY (expert_user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部服务报告评价信用证据';

INSERT IGNORE INTO sys_role
    (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     status, del_flag, create_by, create_time, remark)
VALUES
    (1003, '业务经办人员', 'business_operator', 30, '1', 1,
     '0', '0', 'admin', NOW(), '仅通过授权接口查询专家资格并提交业务证据');

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
     create_time, update_by, update_time, remark)
VALUES
    (4200, '专家业务接口', 0, 20, '', NULL, '', '', 1, 0, 'M', '1', '0',
     '', '#', 'admin', NOW(), '', NULL, '业务系统专家接口权限'),
    (4201, '专家资格核验', 4200, 1, '', NULL, '', '', 1, 0, 'F', '1', '0',
     'expert:open:query', '#', 'admin', NOW(), '', NULL, '查询专家档案与资格核验'),
    (4202, '接收业务证据', 4200, 2, '', NULL, '', '', 1, 0, 'F', '1', '0',
     'expert:open:evidence:write', '#', 'admin', NOW(), '', NULL,
     '接收服务报告、评价和信用事实');

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
VALUES
    (1, 4200), (1, 4201), (1, 4202),
    (1003, 4200), (1003, 4201), (1003, 4202);
