-- 专家临时不可用时段与审核版本快照。
-- 1. 日常可联系时间继续使用 expert_schedule；
-- 2. expert_unavailable_period 保存临时不可用时段；
-- 3. expert_release_version 保存每次材料审核通过后的30维画像与知识图谱快照。
USE science_expert;

CREATE TABLE IF NOT EXISTS expert_unavailable_period (
    unavailable_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '临时不可用时段ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    start_time DATETIME NOT NULL COMMENT '不可用开始时间',
    end_time DATETIME NOT NULL COMMENT '不可用结束时间',
    reason VARCHAR(500) DEFAULT NULL COMMENT '不可用原因',
    version INT NOT NULL DEFAULT 0 COMMENT '并发更新版本',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (unavailable_id),
    KEY idx_expert_unavailable_user_time (user_id, start_time, end_time),
    CONSTRAINT fk_expert_unavailable_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家临时不可用时段';

CREATE TABLE IF NOT EXISTS expert_release_version (
    release_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核发布版本ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    version_no INT NOT NULL COMMENT '专家审核版本号',
    graph_version INT NOT NULL DEFAULT 0 COMMENT '当时画像版本号',
    source_material_versions_json LONGTEXT NOT NULL COMMENT '当时生效的材料类别与版本',
    profile_snapshot_json LONGTEXT NOT NULL COMMENT '30维画像快照',
    graph_snapshot_json LONGTEXT NOT NULL COMMENT '知识图谱快照',
    approved_by VARCHAR(64) DEFAULT NULL COMMENT '触发该版本的审核人',
    trigger_type VARCHAR(32) NOT NULL DEFAULT 'MATERIAL_APPROVED'
        COMMENT '版本触发类型',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (release_id),
    UNIQUE KEY uk_expert_release_version (user_id, version_no),
    KEY idx_expert_release_user_time (user_id, create_time),
    CONSTRAINT fk_expert_release_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家画像与知识图谱审核版本快照';
