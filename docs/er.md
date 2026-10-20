# 数据库 ER 说明

> README 5 章约定 `docs/er.png`，当前以 **mermaid 源码**形式维护（便于随代码评审同步修改）。
> 用支持 mermaid 的编辑器（VS Code 插件 / Typora / mermaid.live）可一键导出 PNG 覆盖 `docs/er.png`。

## 1. ER 图（mermaid）

```mermaid
erDiagram
    sys_user ||--o| student : "1:1 学籍"
    sys_user ||--o{ payment_item : "created_by 创建"
    student ||--o{ payment_order : "下单"
    payment_item ||--o{ payment_order : "被缴纳"
    payment_order ||--o{ refund_record : "order_no 退款"
    reconciliation_batch ||--o{ reconciliation_detail : "batch_id 明细"
    notice ||--o{ notice_read : "notice_id 已读"
    sys_user ||--o{ notice_read : "user_id 已读"
    payment_item ||--o{ notice : "item_id 关联(可空)"

    sys_user {
        bigint id PK
        varchar username UK "登录账号：学号/工号"
        varchar password "BCrypt"
        varchar real_name
        varchar role "STUDENT/FINANCE/ADMIN"
        varchar phone
        varchar email
        tinyint status "1启用 0禁用"
        datetime last_login_at
    }

    student {
        bigint id PK
        bigint user_id UK "sys_user.id"
        varchar student_no UK
        varchar college
        varchar major
        varchar class_name
        smallint grade
        varchar id_card "脱敏展示"
        tinyint status "1在读 0离校"
    }

    payment_item {
        bigint id PK
        varchar item_no UK "TX20260001"
        varchar item_name
        varchar category "TUITION/ACCOMMODATION/BOOK/EXAM/OTHER"
        varchar term "2026-2027-1"
        decimal amount
        tinyint allow_partial
        varchar target_scope "ALL/GRADE/COLLEGE/MAJOR/CUSTOM"
        varchar scope_value "JSON 数组"
        datetime start_time
        datetime end_time
        varchar status "DRAFT/PUBLISHED/CLOSED"
        bigint created_by
    }

    payment_order {
        bigint id PK
        varchar order_no UK
        bigint student_id FK
        bigint item_id FK
        decimal amount "金额快照"
        decimal paid_amount
        varchar channel "MOCK/WECHAT/..."
        varchar status "PENDING/PAYING/PAID/CLOSED/REFUNDED/PARTIAL_REFUND"
        varchar transaction_no UK "渠道流水号"
        varchar idempotency_key UK "下单幂等键"
        datetime expire_at
        datetime paid_at
        datetime closed_at
    }

    refund_record {
        bigint id PK
        varchar refund_no UK
        varchar order_no FK
        decimal amount
        varchar reason
        varchar status "PENDING/SUCCESS/FAILED"
        bigint operator_id
        datetime refunded_at
    }

    reconciliation_batch {
        bigint id PK
        varchar batch_no UK
        varchar channel
        date bill_date "UK(channel,bill_date)"
        int total_count
        decimal total_amount
        int matched_count
        int diff_count
        decimal diff_amount
        varchar status "PENDING/PROCESSING/FINISHED"
        varchar file_path
    }

    reconciliation_detail {
        bigint id PK
        bigint batch_id FK
        varchar order_no
        varchar channel_trade_no
        decimal local_amount
        decimal channel_amount
        varchar diff_type "MATCH/LOCAL_ONLY/CHANNEL_ONLY/AMOUNT_DIFF"
        varchar handle_status "UNHANDLED/HANDLED/IGNORED"
        varchar handle_remark
    }

    notice {
        bigint id PK
        varchar title
        text content
        varchar notice_type "SYSTEM/PAYMENT/DEADLINE"
        varchar target_role "STUDENT/FINANCE/ADMIN/ALL"
        bigint item_id FK "可空"
        varchar status "DRAFT/PUBLISHED"
        datetime publish_at
        datetime expire_at
    }

    notice_read {
        bigint id PK
        bigint notice_id FK "UK(notice_id,user_id)"
        bigint user_id FK
        datetime read_at
    }

    operation_log {
        bigint id PK
        bigint user_id "匿名回调为空"
        varchar username
        varchar module
        varchar action
        varchar method
        varchar uri
        varchar ip
        text params "脱敏"
        int result_code
        int cost_ms
        datetime created_at
    }
```

## 2. 索引与唯一约束（幂等与一致性的关键）

| 表 | 约束 | 作用 |
|---|---|---|
| `sys_user` | `uk_username` | 账号唯一 |
| `student` | `uk_user_id`、`uk_student_no` | 一账号一学籍、学号唯一 |
| `payment_item` | `uk_item_no`、`idx_status_end(status,end_time)` | 项目号唯一；学生端可缴列表走该索引 |
| `payment_order` | `uk_order_no` | 订单号唯一 |
| `payment_order` | `uk_transaction_no` | **渠道流水号唯一**：同一笔渠道交易不能落到两个订单（重复支付 → `60003`） |
| `payment_order` | `uk_idempotency` | **下单幂等**：同一 `Idempotency-Key` 并发插入只会成功一次 |
| `payment_order` | `idx_student_status`、`idx_item_status`、`idx_paid_at` | 学生订单列表、按项目统计、按支付时间对账 |
| `refund_record` | `uk_refund_no`、`idx_order_no` | 退款单唯一、按订单查退款 |
| `reconciliation_batch` | `uk_batch_no`、`uk_channel_date` | 同渠道同账单日只允许一个批次（`60009`） |
| `notice_read` | `uk_notice_user` | 已读记录天然幂等（重复插入被吞掉） |

## 3. 状态机

```
payment_item:  DRAFT ──publish──> PUBLISHED ──close──> CLOSED
                   └──(仅 DRAFT 可编辑)──┘

payment_order: PENDING ──pay──> PAYING ──callback OK──> PAID
                  │                │                     │
                  │                └──callback FAIL──> 回退 PENDING
                  ├──close/超时──> CLOSED
                  └──PAID ──部分退款──> PARTIAL_REFUND ──退完──> REFUNDED
                       └──全额退款──> REFUNDED

refund_record: PENDING ──渠道成功──> SUCCESS ／ ──渠道失败──> FAILED

reconciliation_batch: PENDING ──match──> （有差异）PROCESSING ──全部核销──> FINISHED
                                        （无差异）直接 FINISHED
```

## 4. 数据初始化（`data.sql`）

| 账号 | 密码 | 角色 | 姓名 |
|---|---|---|---|
| `admin` | `123456` | ADMIN | 系统管理员 |
| `caiwu01` | `123456` | FINANCE | 张财务 |
| `20230101` | `123456` | STUDENT | 李小明（计算机学院/软件工程/2023） |
| `20230102` | `123456` | STUDENT | 王小红（外国语学院/英语/2023） |

预置 2 个缴费项目：

| 项目号 | 名称 | 金额 | 适用范围 | 分次缴费 |
|---|---|---|---|---|
| `TX20260001` | 2026-2027 学年学费 | 5800.00 | `ALL` 全体 | 否 |
| `SFF20260001` | 2026-2027 学年住宿费 | 1200.00 | `COLLEGE` 计算机学院、外国语学院 | 是 |

> BCrypt 密文：`$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi`（对应明文 `123456`）。
> 如需重新生成：运行 `backend/src/test/java/com/campus/payment/PasswordHashGenerator.java`（README 7.4）。
