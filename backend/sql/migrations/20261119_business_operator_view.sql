-- 业务经办人员专家核验与任务安排工作台（MySQL 8+，可重复执行）。
USE science_expert;

INSERT IGNORE INTO sys_role
    (role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly,
     status, del_flag, create_by, create_time, remark)
VALUES
    (1003, '业务经办人员', 'business_operator', 30, '1', 1,
     '0', '0', 'admin', NOW(), '查看正式专家档案、画像、时间和冲突，并处理本次任务接单与正式安排');

UPDATE sys_role
SET remark = '查看正式专家档案、画像、时间和冲突，并处理本次任务接单与正式安排'
WHERE role_id = 1003;

INSERT IGNORE INTO sys_menu
    (menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
     is_frame, is_cache, menu_type, visible, status, perms, icon, create_by,
     create_time, update_by, update_time, remark)
VALUES
    (4300, '业务专家查询', 0, 2, 'business-expert', 'expert/business/index', '',
     'BusinessExpert', 1, 0, 'C', '0', '0', 'expert:business:view',
     'guide', 'admin', NOW(), '', NULL,
     '业务经办人员查看专家事实并处理本次任务接单与正式安排');

UPDATE sys_menu
SET remark = '业务经办人员查看专家事实并处理本次任务接单与正式安排'
WHERE menu_id = 4300;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
VALUES
    (1, 4300),
    (1003, 4300);

-- 专家档案、画像和长期声明仍只读；业务角色不继承外部证据写入或开放接口功能权限。
DELETE FROM sys_role_menu
WHERE role_id = 1003
  AND menu_id IN (4200, 4201, 4202);
