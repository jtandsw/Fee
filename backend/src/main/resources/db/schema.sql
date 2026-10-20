-- ============================================================
-- 大学生缴费管理系统 建表脚本
-- MySQL 8.0+ / utf8mb4
-- 执行方式见 README 7.5
-- ============================================================
DROP DATABASE IF EXISTS campus_payment;
CREATE DATABASE campus_payment
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;
USE campus_payment;

-- ---------- 1. 系统用户 ----------
CREATE TABLE sys_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)     NOT NULL COMMENT '登录账号：学号/工号',
    password      VARCHAR(100)    NOT NULL COMMENT 'BCrypt 加密后的密码',
    real_name     VARCHAR(50)     NOT NULL COMMENT '真实姓名',
    role          VARCHAR(20)     NOT NULL COMMENT 'STUDENT/FINANCE/ADMIN',
    phone         VARCHAR(20)              DEFAULT NULL COMMENT '手机号',
    email         VARCHAR(100)             DEFAULT NULL COMMENT '邮箱',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    last_login_at DATETIME                 DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT         NOT NULL DEFAULT 0 COMMENT '0正常 1删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_role (role)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='系统用户表';

-- ---------- 2. 学籍信息 ----------
CREATE TABLE student (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '关联 sys_user.id',
    student_no VARCHAR(20)     NOT NULL COMMENT '学号',
    college    VARCHAR(100)    NOT NULL COMMENT '学院',
    major      VARCHAR(100)    NOT NULL COMMENT '专业',
    class_name VARCHAR(50)     NOT NULL COMMENT '班级',
    grade      SMALLINT        NOT NULL COMMENT '入学年级，如 2023',
    id_card    VARCHAR(32)              DEFAULT NULL COMMENT '身份证号（脱敏展示）',
    status     TINYINT         NOT NULL DEFAULT 1 COMMENT '1在读 0离校',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted    TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    UNIQUE KEY uk_student_no (student_no),
    KEY idx_college_major (college, major)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学生学籍信息表';

-- ---------- 3. 缴费项目 ----------
CREATE TABLE payment_item (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    item_no       VARCHAR(32)     NOT NULL COMMENT '项目编号，如 TX20260001',
    item_name     VARCHAR(100)    NOT NULL COMMENT '项目名称',
    category      VARCHAR(20)     NOT NULL COMMENT 'TUITION/ACCOMMODATION/BOOK/EXAM/OTHER',
    term          VARCHAR(20)     NOT NULL COMMENT '学年学期，如 2026-2027-1',
    amount        DECIMAL(10, 2)  NOT NULL COMMENT '应缴金额（元）',
    allow_partial TINYINT         NOT NULL DEFAULT 0 COMMENT '1允许分次缴纳',
    target_scope  VARCHAR(20)     NOT NULL DEFAULT 'ALL' COMMENT 'ALL/GRADE/COLLEGE/MAJOR/CUSTOM',
    scope_value   VARCHAR(1000)            DEFAULT NULL COMMENT '适用范围 JSON 数组',
    start_time    DATETIME        NOT NULL COMMENT '缴费开始时间',
    end_time      DATETIME        NOT NULL COMMENT '缴费截止时间',
    status        VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/CLOSED',
    description   VARCHAR(500)             DEFAULT NULL COMMENT '缴费说明',
    created_by    BIGINT UNSIGNED NOT NULL COMMENT '创建人 sys_user.id',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted       TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_no (item_no),
    KEY idx_status_end (status, end_time),
    KEY idx_term (term)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='缴费项目表';

-- ---------- 4. 缴费订单 ----------
CREATE TABLE payment_order (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(32)     NOT NULL COMMENT '业务订单号',
    student_id      BIGINT UNSIGNED NOT NULL COMMENT '缴费学生 student.id',
    item_id         BIGINT UNSIGNED NOT NULL COMMENT '缴费项目 payment_item.id',
    amount          DECIMAL(10, 2)  NOT NULL COMMENT '应缴金额快照',
    paid_amount     DECIMAL(10, 2)  NOT NULL DEFAULT 0.00 COMMENT '实缴金额',
    channel         VARCHAR(20)     NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK/WECHAT/ALIPAY/UNIONPAY/CASH',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
                    COMMENT 'PENDING/PAYING/PAID/CLOSED/REFUNDED/PARTIAL_REFUND',
    transaction_no  VARCHAR(64)              DEFAULT NULL COMMENT '渠道流水号（回调写入）',
    idempotency_key VARCHAR(64)              DEFAULT NULL COMMENT '下单幂等键',
    expire_at       DATETIME                 DEFAULT NULL COMMENT '订单超时时间',
    paid_at         DATETIME                 DEFAULT NULL COMMENT '支付成功时间',
    closed_at       DATETIME                 DEFAULT NULL COMMENT '关闭时间',
    remark          VARCHAR(255)             DEFAULT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_transaction_no (transaction_no),
    UNIQUE KEY uk_idempotency (idempotency_key),
    KEY idx_student_status (student_id, status),
    KEY idx_item_status (item_id, status),
    KEY idx_paid_at (paid_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='缴费订单表';

-- ---------- 5. 退款记录 ----------
CREATE TABLE refund_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    refund_no   VARCHAR(32)     NOT NULL COMMENT '退款单号',
    order_no    VARCHAR(32)     NOT NULL COMMENT '原订单号',
    amount      DECIMAL(10, 2)  NOT NULL COMMENT '退款金额',
    reason      VARCHAR(255)    NOT NULL COMMENT '退款原因',
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SUCCESS/FAILED',
    operator_id BIGINT UNSIGNED NOT NULL COMMENT '操作人',
    refunded_at DATETIME                 DEFAULT NULL COMMENT '退款完成时间',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='退款记录表';

-- ---------- 6. 对账批次 ----------
CREATE TABLE reconciliation_batch (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_no     VARCHAR(32)     NOT NULL COMMENT '批次号',
    channel      VARCHAR(20)     NOT NULL COMMENT '对账渠道',
    bill_date    DATE            NOT NULL COMMENT '账单日期 T-1',
    total_count  INT             NOT NULL DEFAULT 0 COMMENT '账单笔数',
    total_amount DECIMAL(14, 2)  NOT NULL DEFAULT 0.00 COMMENT '账单总额',
    matched_count INT            NOT NULL DEFAULT 0 COMMENT '匹配笔数',
    diff_count   INT             NOT NULL DEFAULT 0 COMMENT '差异笔数',
    diff_amount  DECIMAL(14, 2)  NOT NULL DEFAULT 0.00 COMMENT '差异金额',
    status       VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PROCESSING/FINISHED',
    file_path    VARCHAR(255)             DEFAULT NULL COMMENT '账单文件路径',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (batch_no),
    UNIQUE KEY uk_channel_date (channel, bill_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账批次表';

-- ---------- 7. 对账明细 ----------
CREATE TABLE reconciliation_detail (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    batch_id        BIGINT UNSIGNED NOT NULL,
    order_no        VARCHAR(32)              DEFAULT NULL COMMENT '本地订单号',
    channel_trade_no VARCHAR(64)             DEFAULT NULL COMMENT '渠道流水号',
    local_amount    DECIMAL(10, 2)           DEFAULT NULL,
    channel_amount  DECIMAL(10, 2)           DEFAULT NULL,
    diff_type       VARCHAR(20)     NOT NULL
                    COMMENT 'MATCH/LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_DIFF',
    handle_status   VARCHAR(20)     NOT NULL DEFAULT 'UNHANDLED' COMMENT 'UNHANDLED/HANDLED/IGNORED',
    handle_remark   VARCHAR(255)             DEFAULT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_batch (batch_id),
    KEY idx_diff_type (diff_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='对账明细表';

-- ---------- 8. 通知 ----------
CREATE TABLE notice (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title       VARCHAR(100)    NOT NULL,
    content     TEXT            NOT NULL,
    notice_type VARCHAR(20)     NOT NULL DEFAULT 'SYSTEM' COMMENT 'SYSTEM/PAYMENT/DEADLINE',
    target_role VARCHAR(20)     NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/FINANCE/ADMIN/ALL',
    item_id     BIGINT UNSIGNED          DEFAULT NULL COMMENT '关联缴费项目（可为空）',
    status      VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    publish_at  DATETIME                 DEFAULT NULL,
    expire_at   DATETIME                 DEFAULT NULL,
    created_by  BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_role_status (target_role, status, publish_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通知表';

-- ---------- 9. 通知已读 ----------
CREATE TABLE notice_read (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    notice_id BIGINT UNSIGNED NOT NULL,
    user_id   BIGINT UNSIGNED NOT NULL,
    read_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_user (notice_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='通知已读记录表';

-- ---------- 10. 操作日志 ----------
CREATE TABLE operation_log (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED          DEFAULT NULL,
    username    VARCHAR(50)              DEFAULT NULL,
    module      VARCHAR(50)     NOT NULL COMMENT '模块名',
    action      VARCHAR(50)     NOT NULL COMMENT '动作名',
    method      VARCHAR(10)              DEFAULT NULL COMMENT 'HTTP 方法',
    uri         VARCHAR(255)             DEFAULT NULL,
    ip          VARCHAR(64)              DEFAULT NULL,
    params      TEXT                     DEFAULT NULL COMMENT '请求参数（脱敏）',
    result_code INT                      DEFAULT NULL,
    cost_ms     INT                      DEFAULT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, created_at),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='操作日志表';
