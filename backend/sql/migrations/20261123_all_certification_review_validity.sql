-- 所有启用的认证材料统一采用“审核通过后3个月”有效期。
USE science_expert;

UPDATE expert_certification_angle
SET validity_required = '1'
WHERE enabled = '0';

UPDATE expert_material m
JOIN expert_certification_angle a
  ON a.category_code = m.material_category
 AND a.validity_required = '1'
LEFT JOIN (
    SELECT material_id, MAX(create_time) reviewed_at
    FROM expert_review_log
    WHERE action IN ('PASS', 'ADMIN_APPROVE')
      AND current_status = 'APPROVED'
    GROUP BY material_id
) approved_log ON approved_log.material_id = m.material_id
SET m.valid_from = DATE(COALESCE(approved_log.reviewed_at, m.valid_from, m.update_time)),
    m.valid_until = DATE_ADD(
        DATE(COALESCE(approved_log.reviewed_at, m.valid_from, m.update_time)),
        INTERVAL 3 MONTH
    ),
    m.update_time = NOW()
WHERE m.review_status = 'APPROVED'
  AND (m.valid_from IS NULL OR m.valid_until IS NULL);
