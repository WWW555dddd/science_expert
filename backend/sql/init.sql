-- Science Expert Platform complete schema
-- For a new empty MySQL database only.
CREATE DATABASE IF NOT EXISTS science_expert DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE science_expert;
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET SESSION sql_mode = '';

-- ============================================================
-- Source: ry_20260417.sql
-- ============================================================
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
insert into sys_config values(9, '用户管理-密码字符范围',         'sys.account.chrtype',              '0',             'Y', 'admin', sysdate(), '', null, '默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  start_time          datetime                                  comment '执行开始时间',
  end_time            datetime                                  comment '执行结束时间',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('3', '若依开源框架介绍', '1', '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1773931848342439032/a4d22313_1815095.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>', '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、公告已读记录表
-- ----------------------------
drop table if exists sys_notice_read;
create table sys_notice_read (
  read_id          bigint(20)       not null auto_increment    comment '已读主键',
  notice_id        int(4)           not null                   comment '公告id',
  user_id          bigint(20)       not null                   comment '用户id',
  read_time        datetime         not null                   comment '阅读时间',
  primary key (read_id),
  unique key uk_user_notice (user_id, notice_id)   comment '同一用户同一公告只记录一次'
) engine=innodb auto_increment=1 comment='公告已读记录表';


-- ----------------------------
-- 19、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  form_col_num      int(1)          default 1                  comment '表单布局（单列 双列 三列）',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 20、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';
-- ============================================================
-- Source: quartz.sql
-- ============================================================
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;
-- ============================================================
-- Source: expert_platform_v1.sql
-- ============================================================
-- 专家平台一期：角色、菜单、专家档案与审核链路
-- 适用数据库：MySQL 8+

UPDATE sys_config
SET config_value = 'true'
WHERE config_key = 'sys.account.registerUser';

CREATE TABLE IF NOT EXISTS expert_profile (
    profile_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '档案ID',
    user_id BIGINT NOT NULL COMMENT '系统用户ID',
    real_name VARCHAR(64) NOT NULL COMMENT '专家姓名',
    organization VARCHAR(128) DEFAULT NULL COMMENT '所属单位',
    professional_title VARCHAR(64) DEFAULT NULL COMMENT '职称职务',
    research_field VARCHAR(255) DEFAULT NULL COMMENT '专业方向',
    industry_field VARCHAR(255) DEFAULT NULL COMMENT '产业领域',
    service_region VARCHAR(128) DEFAULT NULL COMMENT '服务区域',
    phone VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
    email VARCHAR(128) DEFAULT NULL COMMENT '电子邮箱',
    career_summary TEXT COMMENT '专家简介',
    review_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '审核状态',
    service_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CERTIFICATION' COMMENT '接单状态',
    profile_completion INT NOT NULL DEFAULT 0 COMMENT '档案完成度',
    latest_submit_time DATETIME DEFAULT NULL COMMENT '最近提交时间',
    approved_time DATETIME DEFAULT NULL COMMENT '审核通过时间',
    graph_version INT NOT NULL DEFAULT 0 COMMENT '正式画像版本',
    version INT NOT NULL DEFAULT 0 COMMENT '档案乐观锁版本',
    del_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '删除标志（0正常 2删除）',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (profile_id),
    UNIQUE KEY uk_expert_profile_user (user_id),
    KEY idx_expert_profile_review_status (review_status),
    KEY idx_expert_profile_service_status (service_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家档案';

CREATE TABLE IF NOT EXISTS expert_schedule (
    schedule_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '时间表ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    weekday INT NOT NULL COMMENT '星期，1-7',
    start_time VARCHAR(5) NOT NULL COMMENT '开始时间 HH:mm',
    end_time VARCHAR(5) NOT NULL COMMENT '结束时间 HH:mm',
    service_mode VARCHAR(32) NOT NULL DEFAULT 'REMOTE' COMMENT '服务方式',
    weekly_capacity INT NOT NULL DEFAULT 1 COMMENT '每周可承接数量',
    effective_from DATE DEFAULT NULL COMMENT '生效日期',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (schedule_id),
    KEY idx_expert_schedule_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家可服务时间表';

CREATE TABLE IF NOT EXISTS expert_material (
    material_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    file_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '服务器文件路径',
    file_type VARCHAR(32) DEFAULT NULL COMMENT '文件类型',
    file_size BIGINT DEFAULT 0 COMMENT '文件大小',
    material_category VARCHAR(64) DEFAULT NULL COMMENT '材料类别',
    version_no INT NOT NULL DEFAULT 1 COMMENT '材料版本',
    job_status VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT 'Agent处理状态',
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态',
    processing_token VARCHAR(64) DEFAULT NULL COMMENT 'Agent处理令牌',
    processing_started_at DATETIME DEFAULT NULL COMMENT 'Agent开始处理时间',
    extracted_fields INT NOT NULL DEFAULT 0 COMMENT '提取字段数',
    issue_count INT NOT NULL DEFAULT 0 COMMENT '预审问题数',
    agent_message VARCHAR(1000) DEFAULT NULL COMMENT 'Agent处理摘要',
    review_comment VARCHAR(1000) DEFAULT NULL COMMENT '审核意见',
    create_by VARCHAR(64) DEFAULT '',
    create_time DATETIME DEFAULT NULL,
    update_by VARCHAR(64) DEFAULT '',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (material_id),
    UNIQUE KEY uk_expert_material_version (user_id, material_category, version_no),
    KEY idx_expert_material_user (user_id),
    KEY idx_expert_material_job_status (job_status),
    KEY idx_expert_material_processing (job_status, processing_started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家资料文件及处理状态';

CREATE TABLE IF NOT EXISTS expert_certification_angle (
    category_code VARCHAR(64) NOT NULL COMMENT '认证角度编码',
    angle_name VARCHAR(64) NOT NULL COMMENT '认证角度名称',
    required_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否必选：1是 0否',
    sort_no INT NOT NULL DEFAULT 0,
    enabled CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否启用：0是 1否',
    description VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (category_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家认证角度定义';

CREATE TABLE IF NOT EXISTS expert_field_mapping (
    mapping_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '映射ID',
    material_id BIGINT NOT NULL COMMENT '材料ID',
    db_field VARCHAR(64) NOT NULL COMMENT '标准字段',
    file_field VARCHAR(128) DEFAULT NULL COMMENT '文件字段',
    extracted_value VARCHAR(1000) DEFAULT NULL COMMENT '提取值（已脱敏）',
    confidence DECIMAL(5,2) NOT NULL DEFAULT 0 COMMENT '置信度',
    evidence VARCHAR(500) DEFAULT NULL COMMENT '证据位置',
    conflict_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否冲突',
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (mapping_id),
    KEY idx_expert_mapping_material (material_id),
    CONSTRAINT fk_expert_mapping_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件字段与标准字段映射';

CREATE TABLE IF NOT EXISTS expert_review_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    material_id BIGINT DEFAULT NULL COMMENT '材料ID',
    action VARCHAR(32) NOT NULL COMMENT '审核动作',
    reviewer_id BIGINT DEFAULT NULL COMMENT '审核人ID',
    reviewer_name VARCHAR(64) DEFAULT NULL COMMENT '审核人',
    previous_status VARCHAR(32) DEFAULT NULL COMMENT '原状态',
    current_status VARCHAR(32) DEFAULT NULL COMMENT '新状态',
    comment VARCHAR(1000) DEFAULT NULL COMMENT '审核意见',
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (log_id),
    KEY idx_expert_review_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家审核记录';

CREATE TABLE IF NOT EXISTS expert_status_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    previous_status VARCHAR(32) DEFAULT NULL COMMENT '原状态',
    current_status VARCHAR(32) NOT NULL COMMENT '新状态',
    reason VARCHAR(500) DEFAULT NULL COMMENT '变更原因',
    credit_related CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否信用相关',
    operator_id BIGINT DEFAULT NULL COMMENT '操作人ID',
    operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人',
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (log_id),
    KEY idx_expert_status_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家接单状态变更记录';

CREATE TABLE IF NOT EXISTS expert_approved (
    approved_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '正式库ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    real_name VARCHAR(64) NOT NULL COMMENT '专家姓名',
    organization VARCHAR(128) DEFAULT NULL COMMENT '所属单位',
    research_field VARCHAR(255) DEFAULT NULL COMMENT '专业方向',
    industry_field VARCHAR(255) DEFAULT NULL COMMENT '产业领域',
    service_region VARCHAR(128) DEFAULT NULL COMMENT '服务区域',
    service_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '接单状态',
    source_material_id BIGINT DEFAULT NULL COMMENT '来源材料',
    graph_version INT NOT NULL DEFAULT 0 COMMENT '画像版本',
    approved_by VARCHAR(64) DEFAULT NULL COMMENT '审核人',
    approved_time DATETIME DEFAULT NULL COMMENT '入库时间',
    snapshot_json LONGTEXT COMMENT '审核快照',
    PRIMARY KEY (approved_id),
    UNIQUE KEY uk_expert_approved_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='已审核专家正式库';

CREATE TABLE IF NOT EXISTS expert_dimension_definition (
    dimension_code VARCHAR(64) NOT NULL COMMENT '维度编码',
    group_code VARCHAR(32) NOT NULL COMMENT '维度分组',
    group_name VARCHAR(64) NOT NULL COMMENT '分组名称',
    dimension_name VARCHAR(64) NOT NULL COMMENT '维度名称',
    weight DECIMAL(5,2) NOT NULL DEFAULT 1 COMMENT '计算权重',
    enabled CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否启用',
    sort_no INT NOT NULL DEFAULT 0,
    description VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (dimension_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家能力画像维度定义';

CREATE TABLE IF NOT EXISTS expert_graph_score (
    score_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    dimension_code VARCHAR(64) NOT NULL,
    score DECIMAL(5,2) NOT NULL DEFAULT 0,
    confidence DECIMAL(5,2) NOT NULL DEFAULT 0,
    evidence VARCHAR(500) DEFAULT NULL,
    graph_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVED',
    graph_version INT NOT NULL DEFAULT 0,
    profile_kind VARCHAR(32) NOT NULL DEFAULT 'LEGACY_PROFILE'
        COMMENT '画像来源类型：LEGACY_PROFILE/EVIDENCE_SCORE',
    conflict_flag CHAR(1) NOT NULL DEFAULT '0' COMMENT '是否存在冲突',
    inference_method VARCHAR(32) NOT NULL DEFAULT 'EVIDENCE_SCORE' COMMENT '评分生成方式',
    source_count INT NOT NULL DEFAULT 1 COMMENT '参与聚合的材料证据数',
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (score_id),
    UNIQUE KEY uk_expert_graph_score (user_id, dimension_code, graph_status),
    KEY idx_expert_graph_user (user_id),
    CONSTRAINT fk_expert_graph_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家30维能力画像得分';

CREATE TABLE IF NOT EXISTS expert_graph_dimension_evidence (
    evidence_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    dimension_code VARCHAR(64) NOT NULL,
    material_id BIGINT NOT NULL,
    graph_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    score DECIMAL(5,2) NOT NULL,
    confidence DECIMAL(5,2) NOT NULL,
    evidence VARCHAR(1000) NOT NULL,
    inference_method VARCHAR(32) NOT NULL DEFAULT 'LLM',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_dimension_material_evidence
        (material_id, dimension_code, graph_status),
    KEY idx_dimension_evidence_user
        (user_id, graph_status, dimension_code),
    CONSTRAINT fk_dimension_evidence_profile FOREIGN KEY (user_id)
        REFERENCES expert_profile(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_dimension_evidence_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='30维画像逐材料证据';

CREATE TABLE IF NOT EXISTS expert_model_call_log (
    call_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '调用ID',
    material_id BIGINT NOT NULL COMMENT '资料ID',
    user_id BIGINT NOT NULL COMMENT '专家用户ID',
    provider VARCHAR(32) NOT NULL COMMENT '模型提供方',
    model VARCHAR(128) NOT NULL COMMENT '模型名称',
    prompt_version VARCHAR(64) NOT NULL COMMENT 'Prompt版本',
    input_summary_json VARCHAR(2000) DEFAULT NULL COMMENT '脱敏后的输入摘要',
    output_json LONGTEXT DEFAULT NULL COMMENT '脱敏后的模型输出',
    latency_ms BIGINT NOT NULL DEFAULT 0 COMMENT '调用耗时毫秒',
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    call_status VARCHAR(16) NOT NULL COMMENT 'SUCCESS/FAILED',
    error_code VARCHAR(64) DEFAULT NULL COMMENT '结构化错误码',
    error_message VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
    create_time DATETIME DEFAULT NULL,
    PRIMARY KEY (call_id),
    KEY idx_model_call_material (material_id, create_time),
    KEY idx_model_call_user (user_id, create_time),
    KEY idx_model_call_status (call_status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型调用审计日志';

CREATE TABLE IF NOT EXISTS expert_object_cleanup (
    cleanup_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '清理任务ID',
    object_uri VARCHAR(512) NOT NULL COMMENT '待删除对象地址',
    task_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/DONE',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    last_error VARCHAR(500) DEFAULT NULL COMMENT '最近错误',
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (cleanup_id),
    UNIQUE KEY uk_expert_object_cleanup_uri (object_uri),
    KEY idx_expert_object_cleanup_due (task_status, next_retry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家MinIO对象删除补偿任务';

INSERT IGNORE INTO sys_role(role_id, role_name, role_key, role_sort, data_scope, menu_check_strictly, status, del_flag, create_by, create_time, remark)
VALUES
(1001, '专家', 'expert', 10, '5', 1, '0', '0', 'admin', NOW(), '专家端角色'),
(1002, '审核人员', 'reviewer', 20, '1', 1, '0', '0', 'admin', NOW(), '专家审核角色');

INSERT IGNORE INTO sys_menu(menu_id, menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, update_by, update_time, remark)
VALUES
(3000, '专家中心', 0, 6, 'expert-center', NULL, '', 'ExpertCenter', 1, 0, 'M', '0', '0', '', 'user', 'admin', NOW(), '', NULL, '专家工作入口'),
(3010, '专家工作台', 3000, 1, 'workspace', 'expert/workspace/index', '', 'ExpertWorkspace', 1, 0, 'C', '0', '0', 'expert:workspace:view', 'dashboard', 'admin', NOW(), '', NULL, '专家工作台'),
(3020, '我的档案', 3000, 2, 'profile', 'expert/profile/index', '', 'ExpertProfile', 1, 0, 'C', '0', '0', 'expert:profile:view', 'education', 'admin', NOW(), '', NULL, '专家档案与时间表'),
(3030, '资料文件', 3000, 3, 'materials', 'expert/materials/index', '', 'ExpertMaterials', 1, 0, 'C', '0', '0', 'expert:material:list', 'upload', 'admin', NOW(), '', NULL, '专家资料上传和处理进度'),
(3040, '能力画像', 3000, 4, 'graph', 'expert/graph/index', '', 'ExpertGraph', 1, 0, 'C', '0', '0', 'expert:graph:view', 'chart', 'admin', NOW(), '', NULL, '30维能力画像'),
(3050, '接单状态', 3000, 5, 'status', 'expert/status/index', '', 'ExpertStatus', 1, 0, 'C', '0', '0', 'expert:status:view', 'switch', 'admin', NOW(), '', NULL, '暂停和恢复接单'),
(3500, '审核中心', 0, 7, 'review-center', NULL, '', 'ReviewCenter', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', NOW(), '', NULL, '审核工作入口'),
(3510, '审核工作台', 3500, 1, 'workspace', 'review/workspace/index', '', 'ReviewWorkspace', 1, 0, 'C', '0', '0', 'review:workspace:view', 'dashboard', 'admin', NOW(), '', NULL, '审核工作台'),
(3520, '审核队列', 3500, 2, 'queue', 'review/queue/index', '', 'ReviewQueue', 1, 0, 'C', '0', '0', 'review:queue:list', 'list', 'admin', NOW(), '', NULL, '待审核专家'),
(3530, '已审核专家', 3500, 3, 'approved', 'review/approved/index', '', 'ReviewApproved', 1, 0, 'C', '0', '0', 'review:approved:list', 'checkbox', 'admin', NOW(), '', NULL, '正式专家库'),
(4000, '管理控制台', 0, 8, 'management-center', 'expert/admin/index', '', 'ManagementCenter', 1, 0, 'C', '0', '0', 'expert:admin:view', 'monitor', 'admin', NOW(), '', NULL, '平台管理员工作台'),
(4100, '专家管理', 0, 1, 'expert-management', NULL, '', 'ExpertManagement', 1, 0, 'M', '0', '0', '', 'peoples', 'admin', NOW(), '', NULL, '管理员维护全部专家总体情况'),
(4110, '专家总体情况', 4100, 1, 'list', 'expert/admin/experts/index', '', 'ExpertManagementList', 1, 0, 'C', '0', '0', 'expert:admin:expert:list', 'table', 'admin', NOW(), '', NULL, '全部专家档案与认证情况'),
(4111, '维护专家', 4110, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:admin:expert:edit', '#', 'admin', NOW(), '', NULL, ''),
(4112, '删除专家档案', 4110, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:admin:expert:remove', '#', 'admin', NOW(), '', NULL, ''),
(3011, '编辑档案', 3020, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:profile:edit', '#', 'admin', NOW(), '', NULL, ''),
(3012, '上传资料', 3030, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:material:add', '#', 'admin', NOW(), '', NULL, ''),
(3013, '重试处理', 3030, 2, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:material:retry', '#', 'admin', NOW(), '', NULL, ''),
(3014, '变更接单状态', 3050, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'expert:status:change', '#', 'admin', NOW(), '', NULL, ''),
(3511, '执行审核', 3520, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'review:action', '#', 'admin', NOW(), '', NULL, ''),
(3512, '重试Agent任务', 3510, 1, '', NULL, '', '', 1, 0, 'F', '0', '0', 'review:agent:retry', '#', 'admin', NOW(), '', NULL, '');

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT 1001, menu_id FROM (
    SELECT 3000 menu_id UNION ALL SELECT 3010 UNION ALL SELECT 3020
    UNION ALL SELECT 3030 UNION ALL SELECT 3040 UNION ALL SELECT 3050
    UNION ALL SELECT 3011 UNION ALL SELECT 3012 UNION ALL SELECT 3013
    UNION ALL SELECT 3014
) expert_menus;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT 1002, menu_id FROM (
    SELECT 3500 menu_id UNION ALL SELECT 3510 UNION ALL SELECT 3520
    UNION ALL SELECT 3530 UNION ALL SELECT 3511 UNION ALL SELECT 3512
) review_menus;

INSERT IGNORE INTO sys_role_menu(role_id, menu_id)
SELECT 1, menu_id FROM (
    SELECT 3500 menu_id UNION ALL SELECT 3510 UNION ALL SELECT 3520
    UNION ALL SELECT 3530 UNION ALL SELECT 4000
    UNION ALL SELECT 3511 UNION ALL SELECT 3512
    UNION ALL SELECT 4100 UNION ALL SELECT 4110 UNION ALL SELECT 4111 UNION ALL SELECT 4112
) admin_menus;

INSERT IGNORE INTO expert_dimension_definition(dimension_code, group_code, group_name, dimension_name, weight, enabled, sort_no, description) VALUES
('QUAL_FIELD_MATCH', 'QUALIFICATION', '资格准入与领域适配', '专业背景匹配', 1.50, '0', 1, '学历、职称、职业资格与专业方向对目标领域的匹配程度'),
('QUAL_CREDENTIAL_COMPLETE', 'QUALIFICATION', '资格准入与领域适配', '有效资质完备', 1.30, '0', 2, '身份、学历职称、职业资格等准入材料是否完整且在有效期内'),
('QUAL_RELEVANT_YEARS', 'QUALIFICATION', '资格准入与领域适配', '相关经验年限', 1.10, '0', 3, '从事目标专业、产业或科技保险技术服务的年限'),
('QUAL_INSTITUTION_SUPPORT', 'QUALIFICATION', '资格准入与领域适配', '单位技术支撑', 0.90, '0', 4, '所属单位可提供的实验、检测、专家协作和技术背书能力'),
('TECH_RISK_IDENT', 'TECHNICAL', '专业技术能力', '风险识别', 1.50, '0', 5, '识别标的潜在技术风险、事故诱因和保障需求的能力'),
('TECH_CAUSE_ANALYSIS', 'TECHNICAL', '专业技术能力', '技术机理分析', 1.40, '0', 6, '分析故障、事故、损失成因和技术机理的能力'),
('TECH_METHOD_TOOL', 'TECHNICAL', '专业技术能力', '方法与工具应用', 1.10, '0', 7, '运用检测、仿真、实验、工程分析等方法与工具的能力'),
('TECH_EVIDENCE_COLLECTION', 'TECHNICAL', '专业技术能力', '现场取证与证据分析', 1.30, '0', 8, '现场勘查、样品采集、资料核验和证据链构建能力'),
('TECH_APPRAISAL_LOSS', 'TECHNICAL', '专业技术能力', '技术鉴定与损失核定', 1.50, '0', 9, '形成技术鉴定意见、损失范围和核定依据的能力'),
('IND_ELECTRONICS', 'INDUSTRY', '重点产业覆盖', '电子信息', 1.00, '0', 10, '服务电子信息、半导体、通信和软件相关产业的能力'),
('IND_NONFERROUS', 'INDUSTRY', '重点产业覆盖', '有色金属', 1.00, '0', 11, '服务有色金属采选、冶炼、加工及相关装备的能力'),
('IND_EQUIPMENT', 'INDUSTRY', '重点产业覆盖', '装备制造', 1.00, '0', 12, '服务智能装备、工业制造和自动化系统的能力'),
('IND_NEW_ENERGY', 'INDUSTRY', '重点产业覆盖', '新能源', 1.00, '0', 13, '服务新能源、储能、电力电子和绿色能源产业的能力'),
('IND_BIOMED', 'INDUSTRY', '重点产业覆盖', '生物医药', 1.00, '0', 14, '服务生物医药、医疗器械和健康技术产业的能力'),
('IND_CHEM_MATERIAL', 'INDUSTRY', '重点产业覆盖', '化工与新材料', 1.00, '0', 15, '服务化工中试、新材料研发和工程验证的能力'),
('SCENE_PRE_SURVEY', 'SCENARIO', '保险服务场景', '保前风险勘查', 1.50, '0', 16, '承保前开展技术风险识别、现场勘查和风险建议的能力'),
('SCENE_RISK_REDUCTION', 'SCENARIO', '保险服务场景', '保中风险减量', 1.40, '0', 17, '承保期间开展隐患治理、风险监测和减量服务的能力'),
('SCENE_CLAIM_INSPECTION', 'SCENARIO', '保险服务场景', '出险查勘', 1.50, '0', 18, '事故发生后开展现场查勘、事实核验和原因判断的能力'),
('SCENE_TECH_APPRAISAL', 'SCENARIO', '保险服务场景', '技术鉴定服务', 1.50, '0', 19, '面向保险理赔和争议处理提供独立技术鉴定的能力'),
('SCENE_TECH_RESCUE', 'SCENARIO', '保险服务场景', '技术救助', 1.30, '0', 20, '为事故处置、止损、恢复和技术救援提供专业支持的能力'),
('SERVICE_REGION', 'DELIVERY', '履约交付与合规', '区域覆盖', 1.20, '0', 21, '能够及时到达或远程支持目标服务区域的能力'),
('SERVICE_RESPONSE', 'DELIVERY', '履约交付与合规', '响应时效', 1.40, '0', 22, '接单确认、到场和成果反馈的及时性'),
('SERVICE_MODE', 'DELIVERY', '履约交付与合规', '远程与现场适配', 0.90, '0', 23, '按任务要求提供远程研判或现场服务的能力'),
('SERVICE_COMPLIANCE', 'DELIVERY', '履约交付与合规', '规范与保密合规', 1.50, '0', 24, '遵守服务规范、保密要求、回避规则和证据留存要求'),
('SERVICE_COST', 'DELIVERY', '履约交付与合规', '费用合理性', 1.10, '0', 25, '服务报价与工作量、专家级别及市场标准相匹配的程度'),
('PERF_SIMILAR_PROJECT', 'PERFORMANCE', '历史实绩与知识复用', '同类项目经验', 1.50, '0', 26, '完成同类产业、风险类型和服务场景项目的经验'),
('PERF_REPORT_QUALITY', 'PERFORMANCE', '历史实绩与知识复用', '历史报告质量', 1.50, '0', 27, '技术报告、鉴定意见和风险建议的完整性、规范性与可用性'),
('PERF_COMPLETION_CREDIT', 'PERFORMANCE', '历史实绩与知识复用', '履约与信用', 1.50, '0', 28, '任务完成率、按期履约、投诉和违规记录的综合表现'),
('PERF_COLLABORATION', 'PERFORMANCE', '历史实绩与知识复用', '多专家协同', 1.00, '0', 29, '跨单位、跨专业协同和成果汇总能力'),
('PERF_KNOWLEDGE_REUSE', 'PERFORMANCE', '历史实绩与知识复用', '案例与知识复用', 1.20, '0', 30, '沉淀案例、报告模板、标准、专利或方法并在新任务中复用的能力');

INSERT IGNORE INTO expert_certification_angle(category_code, angle_name, required_flag, sort_no, enabled, description) VALUES
('IDENTITY', '身份证明', '1', 1, '0', '身份证明或相关身份材料'),
('QUALIFICATION', '学历职称', '1', 2, '0', '学历、学位和职称材料'),
('CERTIFICATE', '职业资格', '1', 3, '0', '专业职业资格或执业证书'),
('ACHIEVEMENT', '代表成果', '1', 4, '0', '代表性项目、成果或业绩材料'),
('OTHER', '其他材料', '0', 5, '0', '补充说明材料，不参与整体认证判定');

UPDATE expert_material
SET job_status = 'REVIEWED', update_time = NOW()
WHERE review_status IN ('APPROVED', 'RETURNED', 'REJECTED')
  AND job_status <> 'REVIEWED';

UPDATE expert_material
SET review_status = 'RETURNED', update_time = NOW()
WHERE review_status = 'REJECTED';

UPDATE expert_material
SET review_status = 'PENDING', update_time = NOW()
WHERE job_status = 'REVIEW_PENDING'
  AND review_status = 'PENDING_REVIEW';

UPDATE expert_profile
SET profile_completion = 100
WHERE profile_completion > 100;

UPDATE expert_profile
SET service_status = 'PAUSED', update_time = NOW()
WHERE service_status = 'LIMITED';

-- 管理端菜单收敛：隐藏未使用的若依示例、系统工具和旧专家CRUD入口。
UPDATE sys_menu
SET visible = '1'
WHERE menu_id IN (
    4,
    2, 109, 110, 111, 112, 113, 114,
    1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054,
    3, 115, 116, 117, 1055, 1056, 1057, 1058, 1059, 1060,
    102, 103, 104, 105, 106, 107,
    1012, 1013, 1014, 1015,
    1016, 1017, 1018, 1019,
    1020, 1021, 1022, 1023, 1024,
    1025, 1026, 1027, 1028, 1029,
    1030, 1031, 1032, 1033, 1034,
    1035, 1036, 1037, 1038,
    2000, 2001, 2002, 2003, 2004, 2005, 2006
);

UPDATE sys_menu SET order_num = 0 WHERE menu_id = 4000;
UPDATE sys_menu SET order_num = 1 WHERE menu_id = 4100;
UPDATE sys_menu SET order_num = 2 WHERE menu_id = 1;
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 3000;
UPDATE sys_menu SET order_num = 4 WHERE menu_id = 3500;
UPDATE sys_menu SET order_num = 3 WHERE menu_id = 108;

DELETE rm
FROM sys_role_menu rm
JOIN sys_menu m ON m.menu_id = rm.menu_id
WHERE rm.role_id = 1
  AND m.menu_id IN (3000, 3010, 3020, 3030, 3040, 3050, 3011, 3012, 3013, 3014);

-- ============================================================
-- Source: expert_knowledge_graph_v1.sql
-- ============================================================
-- 专家领域知识图谱 V1：本体版本、实体、关系、证据、Outbox 与能力映射
-- 适用数据库：MySQL 8+
-- 本脚本不删除或修改现有专家业务数据，可重复执行建表部分。

CREATE TABLE IF NOT EXISTS kg_ontology_version (
    version_code VARCHAR(64) NOT NULL COMMENT '本体版本编码',
    display_name VARCHAR(128) NOT NULL COMMENT '本体显示名称',
    resource_path VARCHAR(255) NOT NULL COMMENT '版本化本体资源路径',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RETIRED',
    published_at DATETIME NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (version_code),
    KEY idx_kg_ontology_status (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱本体版本';

CREATE TABLE IF NOT EXISTS kg_entity (
    entity_id BIGINT NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(64) NOT NULL COMMENT '实体类型',
    external_key VARCHAR(128) NOT NULL COMMENT '稳定业务键',
    canonical_name VARCHAR(255) NOT NULL COMMENT '标准名称',
    normalized_name VARCHAR(255) NOT NULL COMMENT '归一化名称',
    properties_json JSON DEFAULT NULL COMMENT '实体属性',
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    ontology_version VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (entity_id),
    UNIQUE KEY uk_kg_entity_external (entity_type, external_key),
    KEY idx_kg_entity_name (entity_type, normalized_name),
    KEY idx_kg_entity_review (review_status, ontology_version),
    CONSTRAINT fk_kg_entity_ontology FOREIGN KEY (ontology_version)
        REFERENCES kg_ontology_version(version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱实体';

CREATE TABLE IF NOT EXISTS kg_entity_alias (
    alias_id BIGINT NOT NULL AUTO_INCREMENT,
    entity_id BIGINT NOT NULL,
    alias_name VARCHAR(255) NOT NULL,
    normalized_alias VARCHAR(255) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_ref VARCHAR(255) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (alias_id),
    UNIQUE KEY uk_kg_alias (entity_id, normalized_alias),
    KEY idx_kg_alias_lookup (normalized_alias),
    CONSTRAINT fk_kg_alias_entity FOREIGN KEY (entity_id)
        REFERENCES kg_entity(entity_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱实体别名';

CREATE TABLE IF NOT EXISTS kg_relation (
    relation_id BIGINT NOT NULL AUTO_INCREMENT,
    source_entity_id BIGINT NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    target_entity_id BIGINT NOT NULL,
    confidence DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    valid_from DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_to DATETIME DEFAULT NULL,
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/REJECTED',
    ontology_version VARCHAR(64) NOT NULL,
    source_kind VARCHAR(32) NOT NULL COMMENT 'PROFILE_PROJECTION/RULE/LLM/MANUAL',
    properties_json JSON DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (relation_id),
    UNIQUE KEY uk_kg_relation_fact (source_entity_id, relation_type, target_entity_id),
    KEY idx_kg_relation_source (source_entity_id, relation_type),
    KEY idx_kg_relation_target (target_entity_id, relation_type),
    CONSTRAINT fk_kg_relation_source FOREIGN KEY (source_entity_id)
        REFERENCES kg_entity(entity_id) ON DELETE CASCADE,
    CONSTRAINT fk_kg_relation_target FOREIGN KEY (target_entity_id)
        REFERENCES kg_entity(entity_id) ON DELETE CASCADE,
    CONSTRAINT fk_kg_relation_ontology FOREIGN KEY (ontology_version)
        REFERENCES kg_ontology_version(version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱关系';

CREATE TABLE IF NOT EXISTS kg_evidence (
    evidence_id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_key VARCHAR(128) NOT NULL COMMENT '幂等证据键',
    relation_id BIGINT DEFAULT NULL,
    entity_id BIGINT DEFAULT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'MATERIAL/EXPERT_PROFILE/SERVICE_LOG/MANUAL',
    source_ref VARCHAR(255) NOT NULL COMMENT '材料、档案或服务记录业务键',
    material_id BIGINT DEFAULT NULL,
    page_no INT DEFAULT NULL,
    char_start INT DEFAULT NULL,
    char_end INT DEFAULT NULL,
    quote_hash VARCHAR(64) DEFAULT NULL,
    excerpt VARCHAR(1000) DEFAULT NULL,
    confidence DECIMAL(5,2) NOT NULL DEFAULT 1.00,
    extractor_version VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (evidence_id),
    UNIQUE KEY uk_kg_evidence_key (evidence_key),
    KEY idx_kg_evidence_relation (relation_id),
    KEY idx_kg_evidence_entity (entity_id),
    KEY idx_kg_evidence_material (material_id),
    CONSTRAINT fk_kg_evidence_relation FOREIGN KEY (relation_id)
        REFERENCES kg_relation(relation_id) ON DELETE CASCADE,
    CONSTRAINT fk_kg_evidence_entity FOREIGN KEY (entity_id)
        REFERENCES kg_entity(entity_id) ON DELETE CASCADE,
    CONSTRAINT fk_kg_evidence_material FOREIGN KEY (material_id)
        REFERENCES expert_material(material_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱事实证据';

CREATE TABLE IF NOT EXISTS kg_change_outbox (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    payload_json JSON DEFAULT NULL,
    event_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/DONE/FAILED',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at DATETIME DEFAULT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_kg_outbox_due (event_status, available_at),
    KEY idx_kg_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱变更 Outbox';

CREATE TABLE IF NOT EXISTS kg_dimension_capability_map (
    dimension_code VARCHAR(64) NOT NULL,
    capability_code VARCHAR(96) NOT NULL,
    capability_name VARCHAR(128) NOT NULL,
    ontology_version VARCHAR(64) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dimension_code),
    UNIQUE KEY uk_kg_capability_code (capability_code),
    CONSTRAINT fk_kg_capability_dimension FOREIGN KEY (dimension_code)
        REFERENCES expert_dimension_definition(dimension_code) ON DELETE CASCADE,
    CONSTRAINT fk_kg_capability_ontology FOREIGN KEY (ontology_version)
        REFERENCES kg_ontology_version(version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='30维画像到标准能力节点映射';

INSERT IGNORE INTO kg_ontology_version
    (version_code, display_name, resource_path, status, published_at, create_time)
VALUES
    ('expert-domain-v1', '科技保险专家领域本体 V1',
     'classpath:knowledge-graph/ontology-v1.json', 'ACTIVE', NOW(), NOW());

INSERT IGNORE INTO kg_dimension_capability_map
    (dimension_code, capability_code, capability_name, ontology_version, create_time)
SELECT dimension_code,
       CONCAT('CAP_', dimension_code),
       dimension_name,
       'expert-domain-v1',
       NOW()
FROM expert_dimension_definition
WHERE enabled = '0';

-- ============================================================
-- Source: expert_agent_runtime_v1.sql
-- ============================================================
-- 专家图谱 GraphRAG Agent 运行审计
-- 适用数据库：MySQL 8+

CREATE TABLE IF NOT EXISTS expert_agent_run (
    run_id VARCHAR(64) NOT NULL,
    objective TEXT NOT NULL,
    run_status VARCHAR(16) NOT NULL DEFAULT 'RUNNING'
        COMMENT 'RUNNING/COMPLETED/FAILED/MAX_STEPS',
    model VARCHAR(128) DEFAULT NULL,
    max_steps INT NOT NULL DEFAULT 6,
    step_count INT NOT NULL DEFAULT 0,
    final_answer LONGTEXT DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id),
    KEY idx_agent_run_status (run_status, create_time),
    KEY idx_agent_run_creator (create_by, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱 Agent 运行记录';

CREATE TABLE IF NOT EXISTS expert_agent_step (
    step_id BIGINT NOT NULL AUTO_INCREMENT,
    run_id VARCHAR(64) NOT NULL,
    step_no INT NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    tool_input_json LONGTEXT DEFAULT NULL,
    observation_json LONGTEXT DEFAULT NULL,
    step_status VARCHAR(16) NOT NULL DEFAULT 'SUCCESS'
        COMMENT 'SUCCESS/FAILED',
    latency_ms BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (step_id),
    KEY idx_agent_step_run (run_id, step_no),
    CONSTRAINT fk_agent_step_run FOREIGN KEY (run_id)
        REFERENCES expert_agent_run(run_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱 Agent 工具调用轨迹';

-- ============================================================
-- Source: expert_quality_evaluation_v1.sql
-- ============================================================
-- 专家抽取质量与画像一致性评估

CREATE TABLE IF NOT EXISTS expert_eval_dataset (
    dataset_id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_name VARCHAR(128) NOT NULL,
    dataset_version VARCHAR(32) NOT NULL,
    description VARCHAR(1000) DEFAULT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    create_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (dataset_id),
    UNIQUE KEY uk_eval_dataset_version (dataset_name, dataset_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家质量评估数据集';

CREATE TABLE IF NOT EXISTS expert_eval_case (
    case_id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    case_name VARCHAR(128) NOT NULL,
    gold_fields_json JSON DEFAULT NULL,
    gold_entities_json JSON DEFAULT NULL,
    gold_relations_json JSON DEFAULT NULL,
    gold_conflicts_json JSON DEFAULT NULL,
    notes VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (case_id),
    UNIQUE KEY uk_eval_case_user (dataset_id, user_id),
    CONSTRAINT fk_eval_case_dataset FOREIGN KEY (dataset_id)
        REFERENCES expert_eval_dataset(dataset_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家质量评估案例';

CREATE TABLE IF NOT EXISTS expert_eval_run (
    run_id BIGINT NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT NOT NULL,
    run_status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    case_count INT NOT NULL DEFAULT 0,
    metrics_json JSON DEFAULT NULL,
    report_json LONGTEXT DEFAULT NULL,
    error_message VARCHAR(1000) DEFAULT NULL,
    create_by VARCHAR(64) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id),
    KEY idx_eval_run_dataset (dataset_id, create_time),
    CONSTRAINT fk_eval_run_dataset FOREIGN KEY (dataset_id)
        REFERENCES expert_eval_dataset(dataset_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家质量评估运行';

CREATE TABLE IF NOT EXISTS expert_eval_case_result (
    result_id BIGINT NOT NULL AUTO_INCREMENT,
    run_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    metrics_json JSON DEFAULT NULL,
    detail_json LONGTEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (result_id),
    KEY idx_eval_result_run (run_id),
    CONSTRAINT fk_eval_result_run FOREIGN KEY (run_id)
        REFERENCES expert_eval_run(run_id) ON DELETE CASCADE,
    CONSTRAINT fk_eval_result_case FOREIGN KEY (case_id)
        REFERENCES expert_eval_case(case_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专家质量评估逐案例结果';

INSERT IGNORE INTO expert_eval_dataset
    (dataset_name, dataset_version, description, status, create_by, create_time)
VALUES
    ('已审核专家基线', 'v1',
     '基于人工/管理员审核通过的专家档案和有效图谱事实构建的启动基线；正式论文评测应补充独立人工标注。',
     'ACTIVE', 'system', NOW());

INSERT IGNORE INTO expert_eval_case
    (dataset_id, user_id, case_name, gold_fields_json, gold_entities_json,
     gold_relations_json, gold_conflicts_json, notes, create_time)
SELECT dataset_id,
       106,
       '专家106已审核档案基线',
       JSON_ARRAY(
           JSON_OBJECT('dbField', 'real_name', 'value', 'expert'),
           JSON_OBJECT('dbField', 'organization', 'value', '南昌大学'),
           JSON_OBJECT('dbField', 'professional_title', 'value', '教授'),
           JSON_OBJECT('dbField', 'research_field', 'value', '软件工程'),
           JSON_OBJECT('dbField', 'industry_field', 'value', '人工智能'),
           JSON_OBJECT('dbField', 'service_region', 'value', '江西省')
       ),
       JSON_ARRAY(
           JSON_OBJECT('entityType', 'Expert', 'name', 'expert'),
           JSON_OBJECT('entityType', 'Organization', 'name', '南昌大学'),
           JSON_OBJECT('entityType', 'Credential', 'name', '教授'),
           JSON_OBJECT('entityType', 'Capability', 'name', '软件工程'),
           JSON_OBJECT('entityType', 'Capability', 'name', '人工智能'),
           JSON_OBJECT('entityType', 'Capability', 'name', '计算机网络'),
           JSON_OBJECT('entityType', 'Capability', 'name', '深度学习'),
           JSON_OBJECT('entityType', 'Industry', 'name', '人工智能'),
           JSON_OBJECT('entityType', 'ServiceRegion', 'name', '江西省')
       ),
       JSON_ARRAY(
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'BELONGS_TO',
                       'targetType', 'Organization', 'targetName', '南昌大学'),
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'HAS_CREDENTIAL',
                       'targetType', 'Credential', 'targetName', '教授'),
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'EXPERTISE_IN',
                       'targetType', 'Capability', 'targetName', '软件工程'),
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'EXPERTISE_IN',
                       'targetType', 'Capability', 'targetName', '人工智能'),
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'WORKS_IN_INDUSTRY',
                       'targetType', 'Industry', 'targetName', '人工智能'),
           JSON_OBJECT('sourceType', 'Expert', 'sourceName', 'expert',
                       'relationType', 'SERVES_REGION',
                       'targetType', 'ServiceRegion', 'targetName', '江西省')
       ),
       JSON_ARRAY(),
       '启动基线来自已审核档案，不能替代独立人工标注集。',
       NOW()
FROM expert_eval_dataset
WHERE dataset_name = '已审核专家基线'
  AND dataset_version = 'v1';

-- ============================================================
-- Source: expert_profile_dimensions_v2.sql
-- ============================================================
-- 科技保险专家30维画像 V2
-- 依据人保专家库立项申请书重构：资格准入、专业技术、重点产业、
-- 保险服务场景、履约交付、历史实绩六组，每组各有明确业务含义。
-- 执行前会把旧画像和逐材料证据复制到 archive 表，再清理派生画像。

CREATE TABLE IF NOT EXISTS expert_graph_score_archive_v1
LIKE expert_graph_score;

INSERT INTO expert_graph_score_archive_v1
SELECT * FROM expert_graph_score;

CREATE TABLE IF NOT EXISTS expert_graph_dimension_evidence_archive_v1
LIKE expert_graph_dimension_evidence;

INSERT INTO expert_graph_dimension_evidence_archive_v1
SELECT * FROM expert_graph_dimension_evidence;

DELETE FROM expert_graph_score;
DELETE FROM expert_graph_dimension_evidence;

DELETE FROM expert_dimension_definition;

INSERT INTO expert_dimension_definition
    (dimension_code, group_code, group_name, dimension_name,
     weight, enabled, sort_no, description)
VALUES
('QUAL_FIELD_MATCH', 'QUALIFICATION', '资格准入与领域适配', '专业背景匹配', 1.50, '0', 1, '学历、职称、职业资格与专业方向对目标领域的匹配程度'),
('QUAL_CREDENTIAL_COMPLETE', 'QUALIFICATION', '资格准入与领域适配', '有效资质完备', 1.30, '0', 2, '身份、学历职称、职业资格等准入材料是否完整且在有效期内'),
('QUAL_RELEVANT_YEARS', 'QUALIFICATION', '资格准入与领域适配', '相关经验年限', 1.10, '0', 3, '从事目标专业、产业或科技保险技术服务的年限'),
('QUAL_INSTITUTION_SUPPORT', 'QUALIFICATION', '资格准入与领域适配', '单位技术支撑', 0.90, '0', 4, '所属单位可提供的实验、检测、专家协作和技术背书能力'),

('TECH_RISK_IDENT', 'TECHNICAL', '专业技术能力', '风险识别', 1.50, '0', 5, '识别标的潜在技术风险、事故诱因和保障需求的能力'),
('TECH_CAUSE_ANALYSIS', 'TECHNICAL', '专业技术能力', '技术机理分析', 1.40, '0', 6, '分析故障、事故、损失成因和技术机理的能力'),
('TECH_METHOD_TOOL', 'TECHNICAL', '专业技术能力', '方法与工具应用', 1.10, '0', 7, '运用检测、仿真、实验、工程分析等方法与工具的能力'),
('TECH_EVIDENCE_COLLECTION', 'TECHNICAL', '专业技术能力', '现场取证与证据分析', 1.30, '0', 8, '现场勘查、样品采集、资料核验和证据链构建能力'),
('TECH_APPRAISAL_LOSS', 'TECHNICAL', '专业技术能力', '技术鉴定与损失核定', 1.50, '0', 9, '形成技术鉴定意见、损失范围和核定依据的能力'),

('IND_ELECTRONICS', 'INDUSTRY', '重点产业覆盖', '电子信息', 1.00, '0', 10, '服务电子信息、半导体、通信和软件相关产业的能力'),
('IND_NONFERROUS', 'INDUSTRY', '重点产业覆盖', '有色金属', 1.00, '0', 11, '服务有色金属采选、冶炼、加工及相关装备的能力'),
('IND_EQUIPMENT', 'INDUSTRY', '重点产业覆盖', '装备制造', 1.00, '0', 12, '服务智能装备、工业制造和自动化系统的能力'),
('IND_NEW_ENERGY', 'INDUSTRY', '重点产业覆盖', '新能源', 1.00, '0', 13, '服务新能源、储能、电力电子和绿色能源产业的能力'),
('IND_BIOMED', 'INDUSTRY', '重点产业覆盖', '生物医药', 1.00, '0', 14, '服务生物医药、医疗器械和健康技术产业的能力'),
('IND_CHEM_MATERIAL', 'INDUSTRY', '重点产业覆盖', '化工与新材料', 1.00, '0', 15, '服务化工中试、新材料研发和工程验证的能力'),

('SCENE_PRE_SURVEY', 'SCENARIO', '保险服务场景', '保前风险勘查', 1.50, '0', 16, '承保前开展技术风险识别、现场勘查和风险建议的能力'),
('SCENE_RISK_REDUCTION', 'SCENARIO', '保险服务场景', '保中风险减量', 1.40, '0', 17, '承保期间开展隐患治理、风险监测和减量服务的能力'),
('SCENE_CLAIM_INSPECTION', 'SCENARIO', '保险服务场景', '出险查勘', 1.50, '0', 18, '事故发生后开展现场查勘、事实核验和原因判断的能力'),
('SCENE_TECH_APPRAISAL', 'SCENARIO', '保险服务场景', '技术鉴定服务', 1.50, '0', 19, '面向保险理赔和争议处理提供独立技术鉴定的能力'),
('SCENE_TECH_RESCUE', 'SCENARIO', '保险服务场景', '技术救助', 1.30, '0', 20, '为事故处置、止损、恢复和技术救援提供专业支持的能力'),

('SERVICE_REGION', 'DELIVERY', '履约交付与合规', '区域覆盖', 1.20, '0', 21, '能够及时到达或远程支持目标服务区域的能力'),
('SERVICE_RESPONSE', 'DELIVERY', '履约交付与合规', '响应时效', 1.40, '0', 22, '接单确认、到场和成果反馈的及时性'),
('SERVICE_MODE', 'DELIVERY', '履约交付与合规', '远程与现场适配', 0.90, '0', 23, '按任务要求提供远程研判或现场服务的能力'),
('SERVICE_COMPLIANCE', 'DELIVERY', '履约交付与合规', '规范与保密合规', 1.50, '0', 24, '遵守服务规范、保密要求、回避规则和证据留存要求'),
('SERVICE_COST', 'DELIVERY', '履约交付与合规', '费用合理性', 1.10, '0', 25, '服务报价与工作量、专家级别及市场标准相匹配的程度'),

('PERF_SIMILAR_PROJECT', 'PERFORMANCE', '历史实绩与知识复用', '同类项目经验', 1.50, '0', 26, '完成同类产业、风险类型和服务场景项目的经验'),
('PERF_REPORT_QUALITY', 'PERFORMANCE', '历史实绩与知识复用', '历史报告质量', 1.50, '0', 27, '技术报告、鉴定意见和风险建议的完整性、规范性与可用性'),
('PERF_COMPLETION_CREDIT', 'PERFORMANCE', '历史实绩与知识复用', '履约与信用', 1.50, '0', 28, '任务完成率、按期履约、投诉和违规记录的综合表现'),
('PERF_COLLABORATION', 'PERFORMANCE', '历史实绩与知识复用', '多专家协同', 1.00, '0', 29, '跨单位、跨专业协同和成果汇总能力'),
('PERF_KNOWLEDGE_REUSE', 'PERFORMANCE', '历史实绩与知识复用', '案例与知识复用', 1.20, '0', 30, '沉淀案例、报告模板、标准、专利或方法并在新任务中复用的能力');

INSERT IGNORE INTO kg_dimension_capability_map
    (dimension_code, capability_code, capability_name, ontology_version, create_time)
SELECT dimension_code,
       CONCAT('CAP_', dimension_code),
       dimension_name,
       'expert-domain-v1',
       NOW()
FROM expert_dimension_definition
WHERE enabled = '0';

UPDATE expert_profile
SET graph_version = 0,
    update_time = NOW();

UPDATE expert_approved
SET graph_version = 0;

-- ============================================================
-- Source: expert_graph_native_profile_v1.sql
-- ============================================================
-- 知识图谱驱动画像 V1
-- 适用：已执行 expert_platform_v1.sql 和 expert_knowledge_graph_v1.sql 的数据库。
-- 该脚本把30维画像改为从图谱中的材料级能力评估声明派生。

INSERT IGNORE INTO kg_ontology_version
    (version_code, display_name, resource_path, status, published_at, create_time)
VALUES
    ('expert-domain-v2', '科技保险专家领域本体 V2',
     'classpath:knowledge-graph/ontology-v2.json', 'ACTIVE', NOW(), NOW());

ALTER TABLE expert_graph_score
    ADD COLUMN graph_relation_ids VARCHAR(2000) DEFAULT NULL
        COMMENT '画像维度引用的图谱关系ID' AFTER source_count,
    ADD COLUMN graph_entity_ids VARCHAR(2000) DEFAULT NULL
        COMMENT '画像维度引用的图谱实体ID' AFTER graph_relation_ids,
    ADD COLUMN graph_evidence_ids VARCHAR(2000) DEFAULT NULL
        COMMENT '画像维度引用的图谱证据ID' AFTER graph_entity_ids,
    ADD COLUMN graph_path_count INT NOT NULL DEFAULT 0
        COMMENT '画像维度可追溯的图谱路径数' AFTER graph_evidence_ids,
    ADD COLUMN profile_rule_version VARCHAR(64) DEFAULT NULL
        COMMENT '画像派生规则版本' AFTER graph_path_count;

ALTER TABLE expert_graph_dimension_evidence
    ADD COLUMN graph_relation_id BIGINT DEFAULT NULL
        COMMENT '材料级画像声明的图谱关系ID' AFTER inference_method,
    ADD COLUMN graph_entity_id BIGINT DEFAULT NULL
        COMMENT '材料级画像声明的图谱实体ID' AFTER graph_relation_id,
    ADD COLUMN graph_evidence_id BIGINT DEFAULT NULL
        COMMENT '材料级画像声明的图谱证据ID' AFTER graph_entity_id,
    ADD COLUMN profile_rule_version VARCHAR(64) DEFAULT NULL
        COMMENT '画像派生规则版本' AFTER graph_evidence_id;

CREATE INDEX idx_dimension_evidence_graph_relation
    ON expert_graph_dimension_evidence (graph_relation_id);

CREATE INDEX idx_dimension_evidence_graph_evidence
    ON expert_graph_dimension_evidence (graph_evidence_id);

UPDATE expert_graph_score
SET profile_rule_version = 'legacy-evidence-score-v1'
WHERE profile_rule_version IS NULL;

UPDATE expert_graph_dimension_evidence
SET profile_rule_version = 'legacy-evidence-score-v1'
WHERE profile_rule_version IS NULL;

-- 验证：
-- SELECT dimension_code, profile_kind, inference_method, graph_path_count,
--        graph_relation_ids, graph_evidence_ids
-- FROM expert_graph_score
-- WHERE user_id = 你的专家用户ID AND graph_status = 'DRAFT';
--
-- SELECT count(*) AS assessment_entities
-- FROM kg_entity
-- WHERE entity_type = 'CapabilityAssessment'
--   AND ontology_version = 'expert-domain-v2';

