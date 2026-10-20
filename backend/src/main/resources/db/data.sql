USE campus_payment;

-- 密码统一为 123456（BCrypt 强度 10）
-- 若登录报“用户名或密码错误”，用 README 7.4 的程序重新生成哈希后替换
SET @pwd = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi';

-- 管理员
INSERT INTO sys_user (username, password, real_name, role, phone, status)
VALUES ('admin', @pwd, '系统管理员', 'ADMIN', '13800000001', 1);

-- 财务人员
INSERT INTO sys_user (username, password, real_name, role, phone, status)
VALUES ('caiwu01', @pwd, '张财务', 'FINANCE', '13800000002', 1);

-- 学生
INSERT INTO sys_user (username, password, real_name, role, phone, status)
VALUES ('20230101', @pwd, '李小明', 'STUDENT', '13800000003', 1),
       ('20230102', @pwd, '王小红', 'STUDENT', '13800000004', 1);

INSERT INTO student (user_id, student_no, college, major, class_name, grade)
SELECT id, username, '计算机学院', '软件工程', '软工2301', 2023
FROM sys_user WHERE username = '20230101';

INSERT INTO student (user_id, student_no, college, major, class_name, grade)
SELECT id, username, '计算机学院', '软件工程', '软工2301', 2023
FROM sys_user WHERE username = '20230102';

-- 一个进行中的缴费项目（全校可缴，期末前截止）
INSERT INTO payment_item (item_no, item_name, category, term, amount, allow_partial,
                          target_scope, scope_value, start_time, end_time, status, description, created_by)
VALUES ('TX20260001', '2026-2027学年第一学期学费', 'TUITION', '2026-2027-1', 5800.00, 0,
        'ALL', NULL, '2026-09-01 00:00:00', '2026-12-31 23:59:59', 'PUBLISHED',
        '请于截止日期前完成缴纳，逾期将影响选课与考试资格。',
        (SELECT id FROM sys_user WHERE username = 'caiwu01'));

INSERT INTO payment_item (item_no, item_name, category, term, amount, allow_partial,
                          target_scope, scope_value, start_time, end_time, status, description, created_by)
VALUES ('SFF20260001', '2026-2027学年住宿费', 'ACCOMMODATION', '2026-2027-1', 1200.00, 1,
        'COLLEGE', '["计算机学院","外国语学院"]', '2026-09-01 00:00:00', '2026-11-30 23:59:59', 'PUBLISHED',
        '支持分两次缴纳，每次不低于 600 元。',
        (SELECT id FROM sys_user WHERE username = 'caiwu01'));

-- 一条通知
INSERT INTO notice (title, content, notice_type, target_role, status, publish_at, created_by)
VALUES ('关于2026-2027学年学费缴纳的通知',
        '各位同学：请在 2026-12-31 前登录系统完成本学期学费缴纳，逾期后果自负。',
        'PAYMENT', 'STUDENT', 'PUBLISHED', NOW(),
        (SELECT id FROM sys_user WHERE username = 'caiwu01'));
