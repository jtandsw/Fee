# 接口补充说明（README 10.4 未覆盖的细节）

> 本文补充 README 第 10 章未展开的实现细节，便于前后端联调与后期维护。
> 在线文档：启动后端后访问 `http://localhost:8080/api/swagger-ui.html`（springdoc 自动生成）。

## 1. 通用约定速查

| 项 | 约定 |
|---|---|
| 前缀 | `http://localhost:8080/api`，业务接口统一 `/v1/{资源复数}` |
| 认证头 | `Authorization: Bearer {accessToken}` |
| 响应体 | `{ "code": 0, "message": "ok", "data": ..., "traceId": "...", "timestamp": "..." }` |
| 分页入参 | `pageNum`（从 1 开始）、`pageSize`（默认 10，最大 100） |
| 分页出参 | `{ "list": [], "total": 0, "pageNum": 1, "pageSize": 10 }` |
| 金额 | 入参可为数字或字符串；**出参一律两位小数字符串**（如 `"5800.00"`），前端用 `MoneyText` 展示 |
| 时间 | 入参出参均为 `yyyy-MM-dd HH:mm:ss`；日期（账单日）为 `yyyy-MM-dd` |
| 幂等 | 下单必须带 `Idempotency-Key`（建议 UUID），缺失返回 `40002` |
| 追踪 | 响应头 `X-Trace-Id` 与 body 的 `traceId` 一致，可用于查日志 |

## 2. 认证模块 `/v1/auth`

| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| POST | `/login` | `{username, password}` | `LoginVO` | 失败 `40103`，禁用 `40104` |
| POST | `/refresh` | `{refreshToken}` | `LoginVO` | refresh **一次性使用**，旧 refresh 立即作废 |
| POST | `/logout` | `{refreshToken}`（可空） | — | access 入黑名单，refresh 作废 |
| GET | `/me` | — | `UserVO` | 前端刷新页面后重新拉取当前用户 |
| PUT | `/password` | `{oldPassword, newPassword}` | — | 成功后建议前端强制重新登录 |

`LoginVO`：`{accessToken, refreshToken, expiresIn, user}`；`expiresIn` 单位秒（默认 7200）。

## 3. 缴费项目 `/v1/payment-items`

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/?status&category&term&keyword&pageNum&pageSize` | FINANCE | 全量分页（含草稿） |
| GET | `/available` | 登录用户 | **学生端**：仅 `PUBLISHED` + 时间窗内 + 命中本人适用范围（`JSON_CONTAINS`）。可选 `payFilter=ALL\|UNPAID\|PAID`（默认 ALL）按本人缴费进度过滤；每条附 `paidAmount`（已支付-**已成功退款**）、`remainAmount`、`payStatus`（`UNPAID`/`PARTIAL`/`PAID`） |
| GET | `/scope-options` | FINANCE | 适用范围下拉可选项 `{grades, colleges, majors[]}`，全部取自学籍档案（`student`）现存值 |
| GET | `/scope-students?keyword&ids` | FINANCE | 「指定学生」候选：`keyword` 按学号/姓名模糊匹配（上限 20 条），`ids=1,2` 用于编辑时回显已选 |
| POST | `/scope-preview` | FINANCE | 传适用范围条件，返回 `{studentCount, text}`；`studentCount=0` 表示发出去没人能看到 |
| GET | `/{id}` | STUDENT/FINANCE | 学生访问非本人范围项目 → `40301` |
| POST | `/` | FINANCE | 创建后为 `DRAFT`，编号「前缀+年份+4 位序号」 |
| PUT | `/{id}` | FINANCE | 仅 `DRAFT` 可改，否则 `60006` |
| POST | `/{id}/publish` | FINANCE | `DRAFT`/`CLOSED → PUBLISHED`；`endTime` 必须晚于当前时间 |
| GET | `/{id}/close-preview` | FINANCE | 下架影响预览：已缴人数/笔数/金额、未完成订单数、`canClose` |
| POST | `/{id}/close` | FINANCE | `PUBLISHED → CLOSED`；存在待支付/支付中订单时拒绝 |

适用范围（**多维度组合**，非空维度取交集，全空 = 全校）：

```json
{
  "scope": {
    "grades":    ["2023"],
    "colleges":  ["计算机学院"],
    "majors":    ["软件工程"],
    "studentIds": ["12", "15"]
  }
}
```

- 维度之间是 **AND**：上例只覆盖「计算机学院 / 软件工程 / 2023 级」且 id 为 12、15 的学生（后者通常不用同时填）。
- 落库：`target_scope='COMPOSITE'`，`scope_value` 存上述对象（只写非空维度）；空对象或 `scope` 不传 = `ALL`。
- 前端不得手填：年级/学院/专业的值来自 `GET /scope-options`，学生来自 `GET /scope-students`。字面与学生档案不一致会导致 `JSON_CONTAINS` 匹配不到。
- 历史数据格式（`GRADE`/`COLLEGE`/`MAJOR`/`CUSTOM` + JSON 数组）仍兼容读取，`GET /{id}` 会把它们统一翻译成 `scope` 四维度结构；老入参 `{targetScope, scopeValue}` 仍可用，但落库会归一为 `COMPOSITE`。

| 维度 | 取值来源 | 匹配字段 |
|---|---|---|
| `grades` | `student.grade`（字符串） | 等值 |
| `colleges` | `student.college` | 等值 |
| `majors` | `student.major` | 等值 |
| `studentIds` | `student.id`（**不是学号**） | 等值 |

## 4. 订单 `/v1/orders`

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| POST | `/` | 登录用户 | 头必带 `Idempotency-Key`；`{itemId, channel?, amount?, remark?}`。订单金额由服务端计算 = **剩余应缴**（项目金额 − 已支付净额），`amount` 入参仅展示用 |
| GET | `/?status&itemId&channel&keyword&pageNum&pageSize` | 登录用户 | 学生**强制**只看本人（`40301` 兜底） |
| GET | `/{orderNo}` | 本人 / FINANCE / ADMIN | 详情 |
| POST | `/{orderNo}/pay` | 本人 | `PENDING → PAYING`，返回 `payUrl + payParams`；超时自动关闭 |
| POST | `/{orderNo}/close` | 本人 | 仅 `PENDING` 可关 |
| POST | `/{orderNo}/refund` | FINANCE/ADMIN | `{amount, reason}`，可退额度 = 订单金额 − 已退成功金额 |
| GET | `/{orderNo}/voucher` | 本人 / FINANCE / ADMIN | 返回 `text/html;charset=UTF-8` + `Content-Disposition: attachment`，前端以 Blob 下载到本地（文件名 `缴费凭证-{orderNo}.html`） |

订单状态机：

```
PENDING ──pay──> PAYING ──callback(SUCCESS)──> PAID ──退款──> PARTIAL_REFUND ──退完──> REFUNDED
   │                 │                            │
   └──close(超时/手动)┴──callback(FAIL)──> PENDING └──全额退款──> REFUNDED
                     └──> CLOSED
```

**下单接口的错误语义**（前端需区分提示）：

| code | 场景 |
|---|---|
| `40002` | 缺 `Idempotency-Key` 请求头 |
| `60001` | 项目不在缴费时间窗内 |
| `60004` | 已缴清无需再缴 |
| `40301` | 学籍信息缺失或不满足适用范围 |
| `40402` | 项目不存在 |

## 5. 支付回调 `/v1/pay/callback/{channel}`

- 匿名放行（渠道服务器调用），`Content-Type: application/json`，请求体原样透传给适配器解析。
- `MOCK` 渠道请求体：`{"orderNo":"...","transactionNo":"...","amount":"5800.00","status":"SUCCESS"}`。
- 幂等语义（重要）：
  - 订单已是 `PAID` → **静默返回成功**（重复回调）；
  - 订单已 `CLOSED`/`REFUNDED`（异常到账）→ 记 `WARN` 日志、**返回成功**、不改状态，差额由对账 `CHANNEL_ONLY` 暴露；
  - 渠道流水号重复使用 → `60003`；
  - 金额与订单金额不一致 → `40001`（防篡改）。

## 6. 对账 `/v1/reconciliations`（FINANCE/ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/?pageNum&pageSize` | 批次分页，按账单日期倒序 |
| POST | `/?channel=MOCK&billDate=2026-03-01` + `multipart file` | 上传 CSV；同渠道同日期唯一（`60009`） |
| POST | `/{batchNo}/match` | 重新匹配（重算明细），已 `FINISHED` 的批次拒绝（`60007`） |
| GET | `/{batchNo}/details?diffType&handleStatus&pageNum&pageSize` | 差异明细 |
| PUT | `/details/{id}/handle` | `{handleStatus: HANDLED\|IGNORED, handleRemark}`，全部处理完批次自动 `FINISHED` |

CSV 格式（首行可带表头，含「订单号」或 `order` 则自动跳过）：

```csv
订单号,渠道流水号,金额
20260301120000123456,WX20260301000001,5800.00
,WX20260301000002,1200.00
```

差异类型：`MATCH`（一致，自动核销）/ `AMOUNT_DIFF`（金额不符）/ `CHANNEL_ONLY`（渠道有本地无）/ `LOCAL_ONLY`（本地有账单无）。

## 7. 统计 `/v1/stats`（FINANCE/ADMIN）

| 方法 | 路径 | 出参要点 |
|---|---|---|
| GET | `/overview?itemId=` | `receivable / received / outstanding / payRate(%) / orderCount / paidCount / studentCount` |
| GET | `/by-item?term=` | 每个项目的应收、实收、订单数、缴费人数 |
| GET | `/by-college` | 每个学院的缴费人数、应收、实收 |
| GET | `/trend?start&end&granularity=day\|month` | `[{statDate, amount, count}]`，非法粒度返回 `40001` |
| GET | `/export` | `application/vnd.openxmlformats...`，文件名 `缴费统计_yyyyMMddHHmmss.xlsx` |

## 8. 通知 `/v1/notices`

| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/?pageNum&pageSize` | 登录用户 | 学生：已发布 + `targetRole` 命中 + 未过期；财务/管理员：全部含草稿 |
| GET | `/unread-count` | 登录用户 | 顶栏红点 |
| GET | `/{id}` | 登录用户 | 详情；学生首次查看自动标记已读（重复无副作用） |
| POST | `/` | FINANCE/ADMIN | `{title, content, noticeType, targetRole, itemId?, expireAt?}`，创建为 `DRAFT` |
| POST | `/{id}/publish` | FINANCE/ADMIN | 发布后学生端可见 |

## 9. 管理端 `/v1/users`、`/v1/logs`（ADMIN）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/users?role&status&keyword&pageNum&pageSize` | 用户分页（含学籍信息） |
| POST | `/users` | 创建；`role=STUDENT` 时学籍字段必填，默认密码 `123456` |
| PUT | `/users/{id}` | 编辑资料（**不允许改角色**） |
| PUT | `/users/{id}/status` | `{status: 1\|0}`，不能禁用自己 |
| PUT | `/users/{id}/reset-password` | `{newPassword}` |
| POST | `/students/import` | `multipart file`，CSV 列：`学号,姓名,学院,专业,班级,年级`，返回 `{total,success,skipped,failed,messages[]}` |
| GET | `/logs?username&module&startDate&endDate&pageNum&pageSize` | 操作日志分页 |

## 10. 环境与健康

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/actuator/health` | `{"status":"UP"}`，dev 下含 db/diskSpace 明细 |
| GET | `/actuator/info` | 应用基础信息 |
