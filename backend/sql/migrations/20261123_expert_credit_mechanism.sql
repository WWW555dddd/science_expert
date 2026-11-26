-- 专家信誉积分、风险扣分、信誉恢复与禁止接单状态（MySQL 8+，可重复执行）。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_expert_credit_score_column//
CREATE PROCEDURE add_expert_credit_score_column()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'expert_profile'
          AND column_name = 'credit_score'
    ) THEN
        ALTER TABLE expert_profile
            ADD COLUMN credit_score INT NOT NULL DEFAULT 100
            COMMENT '信誉积分' AFTER service_status;
    END IF;
END//
DELIMITER ;
CALL add_expert_credit_score_column();
DROP PROCEDURE add_expert_credit_score_column;

CREATE TABLE IF NOT EXISTS expert_credit_log (
    credit_log_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    change_type VARCHAR(32) NOT NULL COMMENT 'RISK_DEDUCTION/RESTORE_APPROVED',
    risk_id BIGINT DEFAULT NULL COMMENT '关联风险预警',
    request_id BIGINT DEFAULT NULL COMMENT '关联状态申请',
    before_score INT NOT NULL,
    after_score INT NOT NULL,
    delta INT NOT NULL,
    reason VARCHAR(500) DEFAULT NULL,
    operator_id BIGINT DEFAULT NULL,
    operator_name VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (credit_log_id),
    UNIQUE KEY uk_credit_risk_deduction (change_type, risk_id),
    KEY idx_credit_log_user_time (user_id, create_time),
    CONSTRAINT fk_credit_log_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家信誉积分变更记录';

UPDATE sys_menu
SET menu_name = '专家状态与信誉审核',
    remark = '审核专家退出与信誉恢复申请'
WHERE menu_id = 3540;
