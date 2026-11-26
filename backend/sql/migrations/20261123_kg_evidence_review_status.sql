-- 图谱关系按材料证据单独确认；避免复用关系的全局采纳状态。
USE science_expert;

DELIMITER //
DROP PROCEDURE IF EXISTS add_kg_evidence_review_status_if_missing//
CREATE PROCEDURE add_kg_evidence_review_status_if_missing()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'kg_evidence'
          AND column_name = 'review_status'
    ) THEN
        ALTER TABLE kg_evidence
            ADD COLUMN review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
            COMMENT 'PENDING/ACCEPTED/REJECTED' AFTER confidence;
    END IF;
END//
DELIMITER ;

CALL add_kg_evidence_review_status_if_missing();
DROP PROCEDURE add_kg_evidence_review_status_if_missing;

UPDATE kg_evidence ev
JOIN expert_material m ON m.material_id = ev.material_id
SET ev.review_status = CASE
        WHEN m.review_status = 'APPROVED' THEN 'ACCEPTED'
        ELSE 'PENDING'
    END
WHERE ev.material_id IS NOT NULL
  AND ev.review_status = 'PENDING';
