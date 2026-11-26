-- 同一材料版本维护多份文件（MySQL 8+，可重复执行）。

CREATE TABLE IF NOT EXISTS expert_material_file (
    file_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '版本内文件ID',
    material_id BIGINT NOT NULL COMMENT '材料版本ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '服务器或MinIO路径',
    file_type VARCHAR(32) DEFAULT NULL COMMENT '文件类型',
    file_size BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '展示顺序',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (file_id),
    KEY idx_expert_material_file_material (material_id, sort_no, file_id),
    CONSTRAINT fk_expert_material_file_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家材料版本内文件';

INSERT INTO expert_material_file
    (material_id, file_name, file_path, file_type, file_size, sort_no,
     create_by, create_time, update_by, update_time)
SELECT m.material_id, m.file_name, m.file_path, m.file_type, m.file_size, 0,
       m.create_by, m.create_time, m.update_by, m.update_time
FROM expert_material m
WHERE NOT EXISTS (
    SELECT 1
    FROM expert_material_file f
    WHERE f.material_id = m.material_id
);
