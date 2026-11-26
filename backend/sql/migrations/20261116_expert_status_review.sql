-- 专家接单状态申请审核链路升级（MySQL 8+，可重复执行）。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_status_request (
    request_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    source_status VARCHAR(32) NOT NULL COMMENT '申请时接单状态',
    target_status VARCHAR(32) NOT NULL COMMENT '目标接单状态',
    request_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELLED',
    reason VARCHAR(500) NOT NULL COMMENT '申请原因',
    credit_related CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否信用相关',
    requester_id BIGINT NOT NULL COMMENT '申请人ID',
    requester_name VARCHAR(64) DEFAULT NULL COMMENT '申请人',
    reviewer_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    reviewer_name VARCHAR(64) DEFAULT NULL COMMENT '审核人',
    review_comment VARCHAR(1000) DEFAULT NULL COMMENT '审核意见',
    request_version INT NOT NULL DEFAULT 0 COMMENT '申请乐观锁版本',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    resolved_time DATETIME DEFAULT NULL COMMENT '处理时间',
    PRIMARY KEY (request_id),
    KEY idx_expert_status_request_user (user_id, request_status),
    KEY idx_expert_status_request_status_time (request_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家接单状态变更申请';

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
     create_time, update_by, update_time, remark)
VALUES
    (3540, '退出专家库审核', 3500, 4, 'status-review', 'review/status/index', '',
     'ReviewStatus', 1, 0, 'C', '0', '0', 'review:action', 'switch',
     'admin', NOW(), '', NULL, '专家退出专家库申请审核');

UPDATE sys_menu
SET menu_name = '退出专家库审核',
    remark = '专家退出专家库申请审核'
WHERE menu_id = 3540;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id) VALUES
    (1001, 3014),
    (1002, 3540),
    (1, 3540);
