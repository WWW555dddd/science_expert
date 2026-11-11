-- 专家基础信息 CRUD 菜单与按钮权限
insert ignore into sys_menu values
(2000, '专家库', 0, 5, 'expert', null, '', 'Expert', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', sysdate(), '', null, '专家库目录');

insert ignore into sys_menu values
(2001, '专家信息', 2000, 1, 'info', 'expert/info/index', '', 'ExpertInfo', 1, 0, 'C', '0', '0', 'expert:info:list', 'user', 'admin', sysdate(), '', null, '专家信息菜单');

insert ignore into sys_menu values
(2002, '专家查询', 2001, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'expert:info:query', '#', 'admin', sysdate(), '', null, '');
insert ignore into sys_menu values
(2003, '专家新增', 2001, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'expert:info:add', '#', 'admin', sysdate(), '', null, '');
insert ignore into sys_menu values
(2004, '专家修改', 2001, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'expert:info:edit', '#', 'admin', sysdate(), '', null, '');
insert ignore into sys_menu values
(2005, '专家删除', 2001, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'expert:info:remove', '#', 'admin', sysdate(), '', null, '');
insert ignore into sys_menu values
(2006, '专家导出', 2001, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'expert:info:export', '#', 'admin', sysdate(), '', null, '');

insert ignore into sys_role_menu(role_id, menu_id)
select role_id, menu_id
from sys_role
cross join (
    select 2000 as menu_id union all
    select 2001 union all
    select 2002 union all
    select 2003 union all
    select 2004 union all
    select 2005 union all
    select 2006
) new_menus
where role_key = 'admin';
