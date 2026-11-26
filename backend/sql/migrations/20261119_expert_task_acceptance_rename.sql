-- 将任务冲突确认升级为专家接单，接单时同时确认利益冲突（MySQL 8+，可重复执行）。
USE science_expert;

UPDATE sys_menu
SET menu_name = '专家接单',
    path = 'expert-acceptance',
    component = 'expert/task-acceptance/index',
    route_name = 'ExpertTaskAcceptance',
    perms = 'expert:task-acceptance:view',
    remark = '专家接受或拒绝具体任务，并确认本次任务利益冲突'
WHERE menu_id = 3060;

UPDATE sys_menu
SET menu_name = '提交接单结果',
    perms = 'expert:task-acceptance:confirm',
    remark = '提交接受拒绝和利益冲突结果'
WHERE menu_id = 3061;

UPDATE expert_task_conflict_confirmation
SET status = 'ACCEPTED'
WHERE status = 'CONFIRMED';

ALTER TABLE expert_task_conflict_confirmation
    COMMENT = '专家任务接单与利益冲突确认';
