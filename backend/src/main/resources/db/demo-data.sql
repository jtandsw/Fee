USE campus_payment;

-- ============================================================
-- 演示 / 测试数据（在 schema.sql + data.sql 之后执行）
-- 覆盖：多学院多年级学生、5 个不同状态项目、32 笔订单（已付/待付/关闭/部分退款/全额退款）、
--       2 条退款记录、1 个已完成的对账批次（含 2 类差异）、5 条通知（含 1 条草稿）
-- 财务口径校验：
--   应收 = 64560.00   实收 = 57320.00   欠费 = 7240.00   缴费率 = 88.79%
-- ============================================================

SET @pwd = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi';
SET @finance_id = (SELECT id FROM sys_user WHERE username = 'caiwu01');

-- ---------- 1. 补充学生账号（data.sql 已有 20230101 李小明、20230102 王小红） ----------
INSERT INTO sys_user (username, password, real_name, role, phone, status)
VALUES ('20230103', @pwd, '赵一鸣', 'STUDENT', '13800000005', 1),
       ('20230201', @pwd, '陈思远', 'STUDENT', '13800000006', 1),
       ('20230202', @pwd, '周雨桐', 'STUDENT', '13800000007', 1),
       ('20230301', @pwd, '吴俊杰', 'STUDENT', '13800000008', 1),
       ('20230302', @pwd, '郑雅静', 'STUDENT', '13800000009', 1),
       ('20240101', @pwd, '孙浩然', 'STUDENT', '13800000010', 1),
       ('20240102', @pwd, '林晓彤', 'STUDENT', '13800000011', 1),
       ('20240201', @pwd, '黄子轩', 'STUDENT', '13800000012', 1);

-- ---------- 2. 学籍信息（3 个学院，便于按学院统计） ----------
INSERT INTO student (user_id, student_no, college, major, class_name, grade)
SELECT u.id, v.no, v.college, v.major, v.class_name, v.grade
FROM sys_user u
         JOIN (SELECT '20230103' AS no, '计算机学院' AS college, '计算机科学与技术' AS major, '计科2301' AS class_name, 2023 AS grade
               UNION ALL SELECT '20230201', '外国语学院', '英语', '英语2301', 2023
               UNION ALL SELECT '20230202', '外国语学院', '商务英语', '商英2302', 2023
               UNION ALL SELECT '20230301', '经济管理学院', '会计学', '会计2301', 2023
               UNION ALL SELECT '20230302', '经济管理学院', '工商管理', '工商2302', 2023
               UNION ALL SELECT '20240101', '计算机学院', '软件工程', '软工2401', 2024
               UNION ALL SELECT '20240102', '外国语学院', '日语', '日语2401', 2024
               UNION ALL SELECT '20240201', '经济管理学院', '金融学', '金融2401', 2024) v
              ON v.no = u.username;

-- ---------- 3. 缴费项目（新增 3 个，与 data.sql 的 2 个合计 5 个） ----------
-- 教材费：10-31 截止，用于演示「3 天内截止提醒」之外的临期项目
INSERT INTO payment_item (item_no, item_name, category, term, amount, allow_partial,
                          target_scope, scope_value, start_time, end_time, status, description, created_by)
VALUES ('JC20260001', '2026-2027学年第一学期教材费', 'BOOK', '2026-2027-1', 480.00, 0,
        'ALL', NULL, '2026-09-01 00:00:00', '2026-10-31 23:59:59', 'PUBLISHED',
        '教材费按学期预收，多退少补，缴费后到班级领取教材。', @finance_id);

-- 考试费：仅 2023 级，演示按年级定向
INSERT INTO payment_item (item_no, item_name, category, term, amount, allow_partial,
                          target_scope, scope_value, start_time, end_time, status, description, created_by)
VALUES ('KS20260001', '2026年下半年全国大学英语四六级考试费', 'EXAM', '2026-2027-1', 120.00, 0,
        'GRADE', '["2023"]', '2026-09-15 00:00:00', '2026-12-15 23:59:59', 'PUBLISHED',
        '四六级考试报名费，缴费成功后不可退，重复缴费请联系财务。', @finance_id);

-- 校园卡押金：草稿状态，演示「未发布项目学生不可见」
INSERT INTO payment_item (item_no, item_name, category, term, amount, allow_partial,
                          target_scope, scope_value, start_time, end_time, status, description, created_by)
VALUES ('YJ20260001', '2026级新生校园卡押金', 'OTHER', '2026-2027-1', 100.00, 0,
        'GRADE', '["2024"]', '2026-11-01 00:00:00', '2026-12-31 23:59:59', 'DRAFT',
        '校园卡押金，毕业离校时凭卡退还，项目尚在审核中。', @finance_id);

-- ---------- 4. 缴费订单（32 笔，金额与状态精心搭配） ----------
INSERT INTO payment_order (order_no, student_id, item_id, amount, paid_amount, channel, status,
                           transaction_no, idempotency_key, expire_at, paid_at, closed_at, remark,
                           created_at, updated_at)
SELECT CONCAT(DATE_FORMAT(v.base_ts, '%Y%m%d%H%i%s'), LPAD(v.seq, 6, '0')),
       s.id,
       i.id,
       v.amount,
       v.paid_amount,
       'MOCK',
       v.status,
       v.txn,
       CONCAT('seed-', v.item_no, '-', v.student_no),
       -- 待支付订单的截止时间放到 7 天后：它们是过去下的单，若不后置会被超时任务立即关闭，
       -- 演示时就没法看到「待支付」状态和收银台流程了
       CASE WHEN v.status = 'PENDING'
                THEN DATE_ADD(NOW(), INTERVAL 7 DAY)
            ELSE DATE_ADD(v.base_ts, INTERVAL 30 MINUTE) END,
       CASE WHEN v.status IN ('PAID', 'PARTIAL_REFUND', 'REFUNDED')
                THEN DATE_ADD(v.base_ts, INTERVAL 5 MINUTE) END,
       CASE WHEN v.status = 'CLOSED' THEN DATE_ADD(v.base_ts, INTERVAL 12 MINUTE) END,
       v.remark,
       v.base_ts,
       v.base_ts
FROM (-- 学费 5800：8 人已缴、1 人待支付（黄子轩未下单）
      SELECT 'TX20260001' AS item_no, '20230101' AS student_no, 5800.00 AS amount, 5800.00 AS paid_amount,
             'PAID' AS status, 1 AS seq, DATE_SUB(NOW(), INTERVAL 38 DAY) AS base_ts, 'MOCK20260001' AS txn, '学费已缴清' AS remark
      UNION ALL SELECT 'TX20260001', '20230102', 5800.00, 5800.00, 'PAID', 2, DATE_SUB(NOW(), INTERVAL 37 DAY), 'MOCK20260002', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20230103', 5800.00, 5800.00, 'PAID', 3, DATE_SUB(NOW(), INTERVAL 36 DAY), 'MOCK20260003', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20230201', 5800.00, 5800.00, 'PAID', 4, DATE_SUB(NOW(), INTERVAL 35 DAY), 'MOCK20260004', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20230202', 5800.00, 5800.00, 'PAID', 5, DATE_SUB(NOW(), INTERVAL 33 DAY), 'MOCK20260005', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20230301', 5800.00, 5800.00, 'PAID', 6, DATE_SUB(NOW(), INTERVAL 30 DAY), 'MOCK20260006', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20230302', 5800.00, 5800.00, 'PAID', 7, DATE_SUB(NOW(), INTERVAL 27 DAY), 'MOCK20260007', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20240101', 5800.00, 5800.00, 'PAID', 8, DATE_SUB(NOW(), INTERVAL 21 DAY), 'MOCK20260008', '学费已缴清'
      UNION ALL SELECT 'TX20260001', '20240102', 5800.00, 0.00, 'PENDING', 9, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, '等待学生支付'
      -- 住宿费 1200：4 人已缴、1 人部分退款、1 人待支付
      UNION ALL SELECT 'SFF20260001', '20230101', 1200.00, 1200.00, 'PAID', 10, DATE_SUB(NOW(), INTERVAL 34 DAY), 'MOCK20260010', '住宿费已缴清'
      UNION ALL SELECT 'SFF20260001', '20230102', 1200.00, 1200.00, 'PAID', 11, DATE_SUB(NOW(), INTERVAL 32 DAY), 'MOCK20260011', '住宿费已缴清'
      UNION ALL SELECT 'SFF20260001', '20230103', 1200.00, 1200.00, 'PAID', 12, DATE_SUB(NOW(), INTERVAL 29 DAY), 'MOCK20260012', '住宿费已缴清'
      UNION ALL SELECT 'SFF20260001', '20240101', 1200.00, 1200.00, 'PAID', 13, DATE_SUB(NOW(), INTERVAL 24 DAY), 'MOCK20260013', '住宿费已缴清'
      UNION ALL SELECT 'SFF20260001', '20230201', 1200.00, 1200.00, 'PARTIAL_REFUND', 14, DATE_SUB(NOW(), INTERVAL 26 DAY), 'MOCK20260014', '走读转住宿调整，已退 600 元'
      UNION ALL SELECT 'SFF20260001', '20230202', 1200.00, 0.00, 'PENDING', 15, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, '等待学生支付'
      -- 教材费 480：9 人已缴、1 人下单后关闭
      UNION ALL SELECT 'JC20260001', '20230101', 480.00, 480.00, 'PAID', 16, DATE_SUB(NOW(), INTERVAL 20 DAY), 'MOCK20260016', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230102', 480.00, 480.00, 'PAID', 17, DATE_SUB(NOW(), INTERVAL 19 DAY), 'MOCK20260017', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230103', 480.00, 480.00, 'PAID', 18, DATE_SUB(NOW(), INTERVAL 18 DAY), 'MOCK20260018', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230201', 480.00, 480.00, 'PAID', 19, DATE_SUB(NOW(), INTERVAL 17 DAY), 'MOCK20260019', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230202', 480.00, 480.00, 'PAID', 20, DATE_SUB(NOW(), INTERVAL 16 DAY), 'MOCK20260020', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230301', 480.00, 480.00, 'PAID', 21, DATE_SUB(NOW(), INTERVAL 15 DAY), 'MOCK20260021', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20230302', 480.00, 480.00, 'PAID', 22, DATE_SUB(NOW(), INTERVAL 14 DAY), 'MOCK20260022', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20240101', 480.00, 480.00, 'PAID', 23, DATE_SUB(NOW(), INTERVAL 13 DAY), 'MOCK20260023', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20240102', 480.00, 480.00, 'PAID', 24, DATE_SUB(NOW(), INTERVAL 12 DAY), 'MOCK20260024', '教材费已缴清'
      UNION ALL SELECT 'JC20260001', '20240201', 480.00, 0.00, 'CLOSED', 25, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, '学生误下单，已自行关闭'
      -- 考试费 120：5 人已缴、1 人全额退款、1 人待支付
      UNION ALL SELECT 'KS20260001', '20230101', 120.00, 120.00, 'PAID', 26, DATE_SUB(NOW(), INTERVAL 11 DAY), 'MOCK20260026', '四级报名费'
      UNION ALL SELECT 'KS20260001', '20230102', 120.00, 120.00, 'PAID', 27, DATE_SUB(NOW(), INTERVAL 10 DAY), 'MOCK20260027', '六级报名费'
      UNION ALL SELECT 'KS20260001', '20230103', 120.00, 120.00, 'PAID', 28, DATE_SUB(NOW(), INTERVAL 8 DAY), 'MOCK20260028', '四级报名费'
      UNION ALL SELECT 'KS20260001', '20230201', 120.00, 120.00, 'PAID', 29, DATE_SUB(NOW(), INTERVAL 7 DAY), 'MOCK20260029', '四级报名费'
      UNION ALL SELECT 'KS20260001', '20230301', 120.00, 120.00, 'PAID', 30, DATE_SUB(NOW(), INTERVAL 6 DAY), 'MOCK20260030', '六级报名费'
      UNION ALL SELECT 'KS20260001', '20230202', 120.00, 120.00, 'REFUNDED', 31, DATE_SUB(NOW(), INTERVAL 5 DAY), 'MOCK20260031', '重复缴费，已全额退款'
      UNION ALL SELECT 'KS20260001', '20230302', 120.00, 0.00, 'PENDING', 32, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, '等待学生支付') v
         JOIN student s ON s.student_no = v.student_no
         JOIN payment_item i ON i.item_no = v.item_no;

-- ---------- 5. 退款记录（2 条已完成） ----------
INSERT INTO refund_record (refund_no, order_no, amount, reason, status, operator_id, refunded_at, created_at, updated_at)
SELECT v.refund_no,
       (SELECT o.order_no FROM payment_order o
        WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = v.item_no)
          AND o.student_id = (SELECT id FROM student WHERE student_no = v.student_no)),
       v.amount, v.reason, 'SUCCESS', @finance_id,
       DATE_SUB(NOW(), INTERVAL v.days_ago DAY),
       DATE_SUB(NOW(), INTERVAL v.days_ago DAY),
       DATE_SUB(NOW(), INTERVAL v.days_ago DAY)
FROM (SELECT 'RF2026092010300001' AS refund_no, 'SFF20260001' AS item_no, '20230201' AS student_no,
             600.00 AS amount, '走读转住宿调整，退部分住宿费' AS reason, 20 AS days_ago
      UNION ALL
      SELECT 'RF2026101815300002', 'KS20260001', '20230202', 120.00, '重复缴费，全额退还考试费', 4) v;

-- ---------- 6. 对账批次（MOCK 渠道，T-1 账单，已完成匹配，含 2 类差异） ----------
INSERT INTO reconciliation_batch (batch_no, channel, bill_date, total_count, total_amount,
                                  matched_count, diff_count, diff_amount, status, file_path, created_at, updated_at)
VALUES (CONCAT('RC', DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '%Y%m%d'), '0001'),
        'MOCK', DATE_SUB(CURDATE(), INTERVAL 1 DAY),
        6, 19540.00, 4, 2, 5820.00, 'FINISHED',
        '/uploads/bill-MOCK-sample.csv', NOW(), NOW());

-- 明细：4 笔一致 + 1 笔渠道多账 + 1 笔金额不一致
INSERT INTO reconciliation_detail (batch_id, order_no, channel_trade_no, local_amount, channel_amount,
                                   diff_type, handle_status, handle_remark, created_at)
SELECT b.id, v.order_no, v.txn, v.local_amount, v.channel_amount, v.diff_type, v.handle_status, v.remark, NOW()
FROM reconciliation_batch b
         JOIN (SELECT (SELECT o.order_no FROM payment_order o
                       WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = 'TX20260001')
                         AND o.student_id = (SELECT id FROM student WHERE student_no = '20230101')) AS order_no,
                      'MOCK20260001' AS txn, 5800.00 AS local_amount, 5800.00 AS channel_amount,
                      'MATCH' AS diff_type, 'HANDLED' AS handle_status, NULL AS remark
               UNION ALL
               SELECT (SELECT o.order_no FROM payment_order o
                       WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = 'TX20260001')
                         AND o.student_id = (SELECT id FROM student WHERE student_no = '20230102')),
                      'MOCK20260002', 5800.00, 5800.00, 'MATCH', 'HANDLED', NULL
               UNION ALL
               SELECT (SELECT o.order_no FROM payment_order o
                       WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = 'SFF20260001')
                         AND o.student_id = (SELECT id FROM student WHERE student_no = '20230103')),
                      'MOCK20260012', 1200.00, 1200.00, 'MATCH', 'HANDLED', NULL
               UNION ALL
               SELECT (SELECT o.order_no FROM payment_order o
                       WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = 'JC20260001')
                         AND o.student_id = (SELECT id FROM student WHERE student_no = '20230101')),
                      'MOCK20260016', 480.00, 480.00, 'MATCH', 'HANDLED', NULL
               UNION ALL
               SELECT NULL, 'MOCK99009001', NULL, 5800.00, 'CHANNEL_ONLY', 'UNHANDLED', '渠道有账单、本地无对应订单，待人工核实'
               UNION ALL
               SELECT (SELECT o.order_no FROM payment_order o
                       WHERE o.item_id = (SELECT id FROM payment_item WHERE item_no = 'JC20260001')
                         AND o.student_id = (SELECT id FROM student WHERE student_no = '20230102')),
                      'MOCK20260017', 480.00, 460.00, 'AMOUNT_DIFF', 'UNHANDLED', '渠道金额少 20 元，待核实优惠抵扣') v
              ON 1 = 1;

-- ---------- 7. 通知（3 条已发布 + 1 条财务内部 + 1 条草稿） ----------
INSERT INTO notice (title, content, notice_type, target_role, item_id, status, publish_at, expire_at, created_by)
VALUES ('缴费系统使用说明（学生版）',
        '登录后进入「缴费项目」选择待缴项目下单，收银台可选支付渠道；缴费成功后可在「我的订单」下载电子缴费凭证。如遇金额异常请先联系财务处核实，不要重复缴费。',
        'SYSTEM', 'STUDENT', NULL,
        'PUBLISHED', DATE_SUB(NOW(), INTERVAL 45 DAY), NULL, @finance_id),
       ('住宿费支持分次缴纳的说明',
        '2026-2027 学年住宿费 1200 元，支持分多次缴纳，每次金额可自行填写，11-30 前缴清即可。',
        'PAYMENT', 'STUDENT', (SELECT id FROM payment_item WHERE item_no = 'SFF20260001'),
        'PUBLISHED', DATE_SUB(NOW(), INTERVAL 30 DAY), '2026-11-30 23:59:59', @finance_id),
       ('教材费缴纳即将截止',
        '本学期教材费 480 元将于 10-31 截止，请尚未缴纳的同学尽快完成，逾期将影响教材发放。',
        'DEADLINE', 'STUDENT', (SELECT id FROM payment_item WHERE item_no = 'JC20260001'),
        'PUBLISHED', DATE_SUB(NOW(), INTERVAL 5 DAY), '2026-10-31 23:59:59', @finance_id),
       ('十月份对账工作安排',
        '请财务处经办人于每月 5 个工作日内完成上月账单导入与差异核销，未核销差异需在月报中说明。',
        'SYSTEM', 'FINANCE', NULL,
        'PUBLISHED', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, @finance_id),
       ('2026 级新生校园卡押金收取通知（草稿）',
        '本项目尚在审核中，发布后学生端才可见，请勿提前告知学生。',
        'PAYMENT', 'STUDENT', (SELECT id FROM payment_item WHERE item_no = 'YJ20260001'),
        'DRAFT', NULL, NULL, @finance_id);

-- ---------- 8. 校验输出 ----------
SELECT '用户' AS 项目, COUNT(*) AS 数量 FROM sys_user
UNION ALL SELECT '学生', COUNT(*) FROM student
UNION ALL SELECT '缴费项目', COUNT(*) FROM payment_item
UNION ALL SELECT '订单', COUNT(*) FROM payment_order
UNION ALL SELECT '退款记录', COUNT(*) FROM refund_record
UNION ALL SELECT '对账批次', COUNT(*) FROM reconciliation_batch
UNION ALL SELECT '对账明细', COUNT(*) FROM reconciliation_detail
UNION ALL SELECT '通知', COUNT(*) FROM notice;

SELECT ROUND(SUM(CASE WHEN status <> 'CLOSED' THEN amount ELSE 0 END), 2) AS 应收,
       ROUND(SUM(CASE WHEN status IN ('PAID', 'PARTIAL_REFUND') THEN paid_amount ELSE 0 END), 2) AS 实收,
       COUNT(*) AS 订单数,
       SUM(CASE WHEN status IN ('PAID', 'PARTIAL_REFUND') THEN 1 ELSE 0 END) AS 已付笔数
FROM payment_order
WHERE deleted = 0;
