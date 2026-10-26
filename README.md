# 大学生缴费管理系统（Campus Payment System）

> 版本：v1.0.0（MVP 基线）　最后更新：2026-10-19
> 一份可直接照着做的开发指导文档：环境可复现、命令可复制、配置不省略。

---

## 目录

1. [项目简介](#1-项目简介)
2. [目标用户与角色权限](#2-目标用户与角色权限)
3. [核心功能范围](#3-核心功能范围)
4. [技术栈与版本要求](#4-技术栈与版本要求)
5. [目录结构约定](#5-目录结构约定)
6. [环境准备与依赖安装](#6-环境准备与依赖安装)
7. [数据库设计与初始化](#7-数据库设计与初始化)
8. [后端启动](#8-后端启动)
9. [前端启动](#9-前端启动)
10. [接口约定](#10-接口约定)
11. [运行与验证](#11-运行与验证)
12. [开发规范](#12-开发规范)
13. [常见问题 FAQ](#13-常见问题-faq)
14. [后续开发计划](#14-后续开发计划)

---

## 1. 项目简介

**一句话定位**：面向高校财务场景的 B/S 架构缴费平台，覆盖「项目发布 → 学生在线缴费 → 订单对账 → 统计报表」的完整闭环，替代线下排队与 Excel 台账。

**解决的问题**

| 现状痛点 | 系统解法 |
|---|---|
| 缴费项目靠通知+线下窗口，学生跑腿 | 项目在线发布，学生宿舍即可缴费 |
| 手工收据、Excel 台账易错漏 | 订单号唯一 + 支付回调幂等，全程可追溯 |
| 财务对账靠人工核对流水 | 渠道账单自动导入、逐笔匹配、差异单列 |
| 缴费情况统计滞后 | 实时看板 + 一键导出报表 |
| 欠费提醒靠群发消息 | 按批次定向通知 + 截止前自动提醒 |

**系统定位**

- 单校/多校区均可部署的**单体应用**（前后端分离），不依赖微服务中间件；
- 支付渠道层做**适配器抽象**（`PaymentChannelAdapter`），当前只实现「模拟支付」一个渠道：不依赖真实商户号即可完整跑通下单、支付、回调、退款；将来接入微信/支付宝时新增一个适配器实现即可，订单与退款代码不动；
- 金额一律 `DECIMAL(10,2)` / Java `BigDecimal`，**禁止 float/double**。

**非目标（MVP 不做）**

- 不做教务/学籍系统的双向同步（仅提供 CSV 导入学生名单）；
- 不做自建支付网关，不做清结算，不做发票开具（仅出电子缴费凭证）；
- 不做多租户 SaaS 化。

---

## 2. 目标用户与角色权限

### 2.1 角色定义

| 角色 | 代码 | 说明 | 典型账号 |
|---|---|---|---|
| 学生 | `STUDENT` | 在校生，本人缴费与查询 | 学号，如 `20230101` |
| 财务人员 | `FINANCE` | 财务处经办人，负责项目、对账、报表 | 工号，如 `caiwu01` |
| 管理员 | `ADMIN` | 系统管理员，负责账号、权限、参数、审计 | `admin` |

> 角色为**单角色模型**（`sys_user.role` 单字段），MVP 不引入 RBAC 多角色表；后续如需扩展见 [14.3](#143-阶段三扩展期可选)。权限校验在后端统一由 Spring Security 的 `hasRole` 完成，前端仅做菜单/按钮的**展示层隐藏**，不作为安全边界。

### 2.2 权限矩阵

| 功能 | 学生 | 财务 | 管理员 |
|---|:--:|:--:|:--:|
| 登录 / 修改本人密码 | ✅ | ✅ | ✅ |
| 查看可缴项目列表 | ✅ 仅本人适用 | ✅ 全部 | ❌ |
| 新建 / 编辑 / 发布 / 下架缴费项目 | ❌ | ✅ | ❌ |
| 创建订单、发起支付 | ✅ 仅本人 | ❌ | ❌ |
| 查看订单 / 缴费记录 | ✅ 仅本人 | ✅ 全部 | ❌ |
| 关闭未支付订单 | ✅ 仅本人 | ❌ | ❌ |
| 退款申请与审核 | ❌ | ✅ | ❌ |
| 对账批次创建 / 差异处理 | ❌ | ✅ | ❌ |
| 统计报表查看 / 导出 | ❌ | ✅ | ❌ |
| 通知发布 | ❌ | ✅ | ❌ |
| 通知接收 | ✅ | ✅ | ✅ |
| 学生名单导入 | ❌ | ❌ | ✅ |
| 用户增删改、重置密码、启禁用 | ❌ | ❌ | ✅ |
| 系统参数 / 数据字典维护 | ❌ | ❌ | ✅ |
| 系统运行信息查看 | ❌ | ❌ | ✅ |
| 操作日志审计 | ❌ | ❌ | ✅ |

> **职责边界**：财务负责"钱"相关的全部事务（项目、订单、退款、对账、报表、通知）；管理员只负责"系统"层面的事务（账号、字典、参数、审计），**不参与也不查看任何财务业务数据**。管理员的默认落地页是"操作日志"，侧边栏不出现任何财务入口。

### 2.3 数据权限（行级）

后端在 Service 层强制注入数据范围，**不依赖前端传参**：

```java
// 学生：强制追加 student_id = 当前登录人
if (Role.STUDENT.equals(currentUser.getRole())) {
    wrapper.eq(PaymentOrder::getStudentId, currentUser.getStudentId());
}
```

- 学生请求他人订单 → 返回 `40301 无权访问该数据`（不要返回 404，避免探测）；
- 财务/管理员默认全量可见，后续可扩展「按学院」数据范围。

---

## 3. 核心功能范围

### 3.1 功能模块总览

```
大学生缴费管理系统
├── 认证与账号       登录 / 登出 / 刷新令牌 / 修改密码 / 找回密码(预留)
├── 缴费项目管理     新建 / 编辑 / 发布 / 下架 / 适用范围设置 / 复制上学期项目
├── 在线缴费         可缴列表 / 下单 / 收银台 / 支付回调 / 支付结果页 / 凭证下载
├── 订单与记录       我的订单 / 全部订单 / 订单详情 / 关闭订单 / 退款
├── 对账管理         账单导入 / 自动匹配 / 差异明细 / 人工核销 / 对账批次归档
├── 统计报表         总览看板 / 按项目 / 按学院 / 按时间趋势 / Excel 导出
├── 通知提醒         通知发布 / 站内消息 / 截止前提醒 / 欠费名单导出
└── 系统管理         用户管理 / 数据字典 / 操作日志 / 系统参数
```

### 3.2 关键业务规则

**（1）缴费项目**

| 字段/规则 | 说明 |
|---|---|
| 项目编号 `item_no` | 全局唯一，格式 `{类别码}{年份}{4位序号}`，如 `TX20260001` |
| 缴费类别 | `TUITION` 学费 / `ACCOMMODATION` 住宿费 / `BOOK` 教材费 / `EXAM` 考试费 / `OTHER` |
| 金额 | `DECIMAL(10,2)`，> 0，上限 99,999,999.99 |
| 适用范围 | **多维度组合条件**：年级 / 学院 / 专业 / 指定学生可任意组合，非空维度之间取**交集**（如「2023 级 + 计算机学院 + 软件工程」），全部留空 = 全校。字段 `target_scope='COMPOSITE'`，`scope_value` 存 `{"grades":[],"colleges":[],"majors":[],"studentIds":[]}`；历史单维度格式（`GRADE`/`COLLEGE`/`MAJOR`/`CUSTOM` + 数组）仍兼容读取。**选项一律由下拉框从学籍档案现存值带出**（`GET /v1/payment-items/scope-options`、`/scope-students`），不可手填；发布前可用 `POST /v1/payment-items/scope-preview` 确认覆盖人数 |
| 时间窗 | `start_time` ≤ `end_time`；仅 `PUBLISHED` 且在时间窗内可缴 |
| 状态流转 | `DRAFT → PUBLISHED → CLOSED`；`CLOSED` 在截止时间前可「重新上架」回到 `PUBLISHED` |
| 部分缴费 | `allow_partial = 1` 时学生可分多次缴纳，每次金额自填，累计不得超过应缴额 |

**（2）订单与支付**（状态机，不可跳转）

```
                    ┌──────────────┐
    下单            │   PENDING    │  超时/主动关闭
   ────────►        │   待支付      │ ──────────────► CLOSED 已关闭
                    └──────┬───────┘
                           │ 发起支付
                           ▼
                    ┌──────────────┐
                    │   PAYING     │  支付失败 → 回退 PENDING（可重试）
                    │   支付中      │
                    └──────┬───────┘
                           │ 回调验签成功
                           ▼
                    ┌──────────────┐   全额退款    ┌──────────────┐
                    │    PAID      │ ────────────► │  REFUNDED    │
                    │   已支付      │               │  已退款       │
                    └──────┬───────┘               └──────────────┘
                           │ 部分退款
                           ▼
                    PARTIAL_REFUND 部分退款
```

- 订单号 `order_no`：`yyyyMMddHHmmss` + 6 位随机数，唯一索引兜底；
- 幂等：下单接口要求 Header `Idempotency-Key`，`payment_order.idempotency_key` 建唯一索引，重复请求直接返回原订单；
- 回调幂等：`transaction_no` 唯一索引 + `UPDATE ... WHERE status = 'PAYING'` 乐观更新，影响行数为 0 视为已处理，直接返回成功；
- 未支付订单 30 分钟自动关闭（定时任务 `OrderCloseJob`，每 5 分钟扫描一次）；
- **金额由服务端校验**：`allow_partial = 1` 的项目可传本次 `amount`（留空表示缴清剩余），服务端校验 `0 < amount ≤ 剩余应缴`；其余项目一律按剩余应缴全额收取，传了不同金额直接拒绝；
- **金额变化会作废旧单**：同一学生、同一项目已有未支付订单时，若本次金额相同则复用该单，不同则关闭旧单再建新单，避免收银台同时挂两张金额不同的订单。

**（3）对账**

1. 每日 01:00 从渠道拉取 T-1 账单文件（CSV），落库 `reconciliation_batch`；
2. 按 `transaction_no` 与本地 `PAID` 订单逐笔匹配，写入 `reconciliation_detail`；
3. 差异类型：`MATCH` 一致 / `LOCAL_ONLY` 本地有渠道无 / `CHANNEL_ONLY` 渠道有本地无 / `AMOUNT_DIFF` 金额不符；
4. 财务在差异明细上做「人工核销」并填写处理说明，批次状态置 `FINISHED` 后归档，**不允许再修改**。

**（4）通知提醒**

- 通知支持定向：`target_role` + `target_scope`（可复用项目的适用范围 JSON）；
- 站内消息已读状态存 `notice_read`；
- 截止前 3 天自动提醒：定时任务扫描 `PUBLISHED` 且 `end_time - now <= 3d` 的项目，向未缴学生推送（MVP 仅站内信，短信/邮件留接口）。

---

## 4. 技术栈与版本要求

### 4.1 版本清单（请严格对齐，避免踩坑）

| 分层 | 技术 | 版本 | 说明 |
|---|---|---|---|
| 语言 | JDK | **17 (LTS)** | Spring Boot 3 最低要求 17，勿用 8/11 |
| 框架 | Spring Boot | **3.2.5** | 内嵌 Tomcat 10.1 |
| 安全 | Spring Security | 6.2.x | 随 Boot 版本 |
| 令牌 | jjwt | **0.12.5** | `jjwt-api` / `jjwt-impl` / `jjwt-jackson` |
| ORM | MyBatis-Plus | **3.5.5** | 必须用 `mybatis-plus-spring-boot3-starter` |
| 数据库 | MySQL | **8.0.33+** | 字符集 `utf8mb4`，时区 `Asia/Shanghai` |
| 驱动 | mysql-connector-j | 8.3.0 | 旧坐标 `mysql:mysql-connector-java` 已废弃 |
| 连接池 | HikariCP | 随 Boot | 默认 |
| 缓存（可选） | Redis | 7.2.x | 令牌黑名单 / 项目缓存，MVP 可关闭 |
| 参数校验 | spring-boot-starter-validation | 随 Boot | Jakarta Validation |
| 接口文档 | springdoc-openapi | **2.5.0** | Boot 3 专用，非 springfox |
| 报表导出 | EasyExcel | 3.3.4 | 流式导出，避免 OOM |
| 工具 | Lombok | 1.18.32 | 需 IDE 插件 |
| 构建 | Maven | **3.9.x** | 需配置阿里云镜像 |
| 前端框架 | Vue | **3.4.x** | Composition API + `<script setup>` |
| 构建工具 | Vite | **5.2.x** | 要求 Node ≥ 18 |
| 语言 | TypeScript | 5.4.x | |
| 状态管理 | Pinia | 2.1.x | 替代 Vuex |
| 路由 | Vue Router | 4.3.x | history 模式 |
| UI 组件 | Element Plus | 2.7.x | 按需引入 |
| 图表 | ECharts | 5.5.x | 统计看板 |
| HTTP | Axios | 1.6.x | 统一拦截器 |
| 运行时 | Node.js | **18.20.x / 20.x LTS** | 20.11.1 推荐 |
| 包管理 | npm | 9.x / 10.x | 或 pnpm 8.x |

### 4.2 后端关键依赖（`backend/pom.xml` 片段）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
    <relativePath/>
</parent>

<properties>
    <java.version>17</java.version>
    <mybatis-plus.version>3.5.5</mybatis-plus.version>
    <jjwt.version>0.12.5</jjwt.version>
    <springdoc.version>2.5.0</springdoc.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <!-- MyBatis-Plus：Boot 3 必须用 spring-boot3-starter -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>${mybatis-plus.version}</version>
    </dependency>
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>${springdoc.version}</version>
    </dependency>
    <dependency>
        <groupId>com.alibaba</groupId>
        <artifactId>easyexcel</artifactId>
        <version>3.3.4</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 4.3 前端关键依赖（`frontend/package.json` 片段）

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vue-tsc --noEmit && vite build",
    "preview": "vite preview --port 4173"
  },
  "dependencies": {
    "vue": "^3.4.27",
    "vue-router": "^4.3.2",
    "pinia": "^2.1.7",
    "element-plus": "^2.7.3",
    "@element-plus/icons-vue": "^2.3.1",
    "axios": "^1.6.8",
    "echarts": "^5.5.0",
    "dayjs": "^1.11.11"
  },
  "devDependencies": {
    "vite": "^5.2.11",
    "@vitejs/plugin-vue": "^5.0.4",
    "typescript": "~5.4.5",
    "vue-tsc": "^2.0.19",
    "unplugin-auto-import": "^0.17.6",
    "unplugin-vue-components": "^0.27.0",
    "sass": "^1.77.0"
  }
}
```

---

## 5. 目录结构约定

```
软件实训/                              # 仓库根目录
├── README.md                          # 本文件
├── .gitignore
├── docs/                              # 设计文档：ER 图、接口补充说明、部署手册
│   ├── er.png
│   └── api-extra.md
├── backend/                           # Spring Boot 后端
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd                # Maven Wrapper（推荐，免装 Maven）
│   └── src/
│       ├── main/
│       │   ├── java/com/campus/payment/
│       │   │   ├── PaymentApplication.java        # 启动类
│       │   │   ├── common/                        # 全局通用
│       │   │   │   ├── Result.java                # 统一响应体
│       │   │   │   ├── PageResult.java
│       │   │   │   ├── ResultCode.java            # 错误码枚举
│       │   │   │   ├── BusinessException.java
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   └── constant/                  # 枚举与常量
│       │   │   │       ├── Role.java
│       │   │   │       ├── OrderStatus.java
│       │   │   │       └── ItemStatus.java
│       │   │   ├── config/                        # 配置类
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   ├── CorsConfig.java
│       │   │   │   ├── MybatisPlusConfig.java     # 分页插件、自动填充
│       │   │   │   ├── OpenApiConfig.java
│       │   │   │   └── WebMvcConfig.java
│       │   │   ├── security/                      # 认证鉴权
│       │   │   │   ├── JwtUtil.java
│       │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   ├── LoginUser.java
│       │   │   │   └── SecurityUtils.java         # 获取当前登录人
│       │   │   ├── controller/                    # 仅做参数接收与返回
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── PaymentItemController.java
│       │   │   │   ├── OrderController.java
│       │   │   │   ├── PayCallbackController.java
│       │   │   │   ├── ReconciliationController.java
│       │   │   │   ├── StatsController.java
│       │   │   │   ├── NoticeController.java
│       │   │   │   └── AdminUserController.java
│       │   │   ├── service/                       # 接口
│       │   │   │   └── impl/                      # 实现（业务逻辑、事务）
│       │   │   ├── mapper/                        # MyBatis-Plus Mapper
│       │   │   ├── entity/                        # 与表一一对应
│       │   │   ├── dto/                           # 入参（含校验注解）
│       │   │   ├── vo/                            # 出参（脱敏、聚合）
│       │   │   ├── payment/                       # 支付渠道适配器
│       │   │   │   ├── PaymentChannelAdapter.java
│       │   │   │   ├── MockChannelAdapter.java
│       │   │   │   └── WechatChannelAdapter.java
│       │   │   ├── job/                           # 定时任务
│       │   │   │   ├── OrderCloseJob.java
│       │   │   │   └── DeadlineNoticeJob.java
│       │   │   └── util/
│       │   └── resources/
│       │       ├── application.yml                # 主配置
│       │       ├── application-dev.yml            # 开发环境
│       │       ├── application-prod.yml           # 生产环境
│       │       ├── mapper/                        # *.xml（复杂 SQL）
│       │       └── db/
│       │           ├── schema.sql                 # 建表脚本
│       │           └── data.sql                   # 初始化数据
│       └── test/java/com/campus/payment/          # 单元/集成测试
├── frontend/                          # Vue 3 前端
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── index.html
│   ├── .env.development               # VITE_API_BASE_URL=/api
│   ├── .env.production                # VITE_API_BASE_URL=/api
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── api/                       # 按模块拆分的接口封装
│       │   ├── auth.ts
│       │   ├── item.ts
│       │   ├── order.ts
│       │   ├── recon.ts
│       │   ├── stats.ts
│       │   └── notice.ts
│       ├── router/
│       │   ├── index.ts
│       │   └── guard.ts               # 登录校验 + 角色路由
│       ├── stores/
│       │   ├── user.ts                # token、用户信息、角色
│       │   └── app.ts
│       ├── views/
│       │   ├── login/index.vue
│       │   ├── student/               # 学生端
│       │   │   ├── Home.vue
│       │   │   ├── ItemList.vue
│       │   │   ├── Cashier.vue
│       │   │   ├── PayResult.vue
│       │   │   └── MyOrders.vue
│       │   ├── finance/               # 财务端
│       │   │   ├── Dashboard.vue
│       │   │   ├── ItemManage.vue
│       │   │   ├── OrderManage.vue
│       │   │   ├── Reconciliation.vue
│       │   │   ├── Report.vue
│       │   │   └── NoticeManage.vue
│       │   ├── admin/                 # 管理端
│       │   │   ├── UserManage.vue
│       │   │   ├── DictManage.vue
│       │   │   └── LogList.vue
│       │   └── error/404.vue
│       ├── components/                # 通用组件（分页、金额展示、状态标签）
│       ├── utils/
│       │   ├── request.ts             # Axios 实例 + 拦截器
│       │   ├── auth.ts                # token 存取
│       │   └── format.ts              # 金额/日期格式化
│       ├── layouts/
│       │   ├── DefaultLayout.vue
│       │   └── components/SideMenu.vue
│       └── styles/
│           └── index.scss
└── deploy/                            # 部署相关（后续阶段）
    ├── nginx.conf
    └── docker-compose.yml
```

### 5.1 分层约束（Code Review 必查）

| 层 | 允许做 | 禁止做 |
|---|---|---|
| `controller` | 参数校验、调用 service、包装 `Result` | 写业务判断、直接调 mapper |
| `service` | 业务逻辑、事务边界、数据权限校验 | 出现 `HttpServletRequest` |
| `mapper` | 单表 CRUD、复杂 SQL | 写业务规则 |
| `entity` | 字段映射 | 暴露给前端（一律转 VO） |

**命名约定**

- 包名全小写：`com.campus.payment.xxx`；
- 类名：`XxxController` / `XxxService` / `XxxServiceImpl` / `XxxMapper` / `XxxDTO` / `XxxVO`；
- 数据库：表名与字段名 `snake_case`，表名不加前缀（除 `sys_` 系统表）；
- 接口路径：`/v1/{资源复数}`，动词用 HTTP 方法表达，**不要** `/v1/getOrderList`。

---

## 6. 环境准备与依赖安装

> 以下命令在 **Windows PowerShell** 与 **macOS/Linux bash** 下均可执行，差异处单独标注。

### 6.1 检查清单

| 依赖 | 最低版本 | 验证命令 |
|---|---|---|
| JDK | 17 | `java -version` |
| Maven | 3.9 | `mvn -v` |
| MySQL | 8.0.33 | `mysql --version` |
| Node.js | 18.20 | `node -v` |
| npm | 9 | `npm -v` |
| Git | 2.30 | `git --version` |

### 6.2 安装 JDK 17

**Windows**：下载 [Temurin 17 (LTS)](https://adoptium.net/temurin/releases/?version=17) 安装包，勾选 *Set JAVA_HOME*，装完新开终端验证。

```bash
java -version
# 期望输出：openjdk version "17.0.x" ...
```

**macOS**

```bash
brew install --cask temurin@17
```

**Ubuntu/Debian**

```bash
sudo apt update && sudo apt install -y openjdk-17-jdk
```

> 若机器上有多个 JDK，务必确认 `JAVA_HOME` 指向 17，否则报 `UnsupportedClassVersionError`。

### 6.3 安装 Maven 并配置阿里云镜像

```bash
mvn -v
# 若无 mvn 命令，Windows 解压 apache-maven-3.9.6-bin.zip 后加入 PATH；macOS 执行 brew install maven
```

编辑 `~/.m2/settings.xml`（Windows 为 `C:\Users\<用户名>\.m2\settings.xml`，不存在则新建）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0
          https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <mirrors>
    <mirror>
      <id>aliyun</id>
      <mirrorOf>central</mirrorOf>
      <name>Aliyun Maven</name>
      <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
  </mirrors>
</settings>
```

### 6.4 安装 MySQL 8 并创建库

**方式 A：本地安装**

- Windows：下载 MySQL Installer → 选 *Server only* → 认证方式选 *Use Strong Password Encryption* → 设置 root 密码（本文档统一使用 `123456`，**生产环境必须修改**）。
- macOS：`brew install mysql@8.0 && brew services start mysql@8.0`
- Ubuntu：`sudo apt install -y mysql-server-8.0`

```bash
# 验证连接
mysql -u root -p -e "SELECT VERSION();"
```

**方式 B：Docker（推荐，环境最干净）**

```bash
docker run -d --name campus-mysql \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  -e TZ=Asia/Shanghai \
  -v campus-mysql-data:/var/lib/mysql \
  mysql:8.0.36 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_general_ci \
  --default-time-zone=+08:00

# 等待约 20 秒后验证
docker exec -it campus-mysql mysql -uroot -p123456 -e "SELECT VERSION();"
```

### 6.5 安装 Node.js

**Windows**：下载 [Node 20 LTS](https://nodejs.org/zh-cn/download) 的 `.msi`，一路默认安装。

**macOS / Linux**：推荐 nvm

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
nvm install 20
nvm use 20
```

配置 npm 镜像（国内必做）：

```bash
npm config set registry https://registry.npmmirror.com
npm config get registry   # 验证
```

### 6.6 获取代码

```bash
git clone <你的仓库地址> 软件实训
cd 软件实训
```

---

## 7. 数据库设计与初始化

### 7.1 设计概览（ER 关系）

```
sys_user 1 ──── 1 student                    （学生账号扩展学籍信息）
sys_user 1 ──── N payment_item               （创建人 created_by）
student  1 ──── N payment_order  N ──── 1 payment_item
payment_order 1 ──── N refund_record
payment_item  N ──── 1 student（通过 target_scope 多对多，JSON 存储）
reconciliation_batch 1 ──── N reconciliation_detail
notice 1 ──── N notice_read ──── 1 sys_user
sys_user 1 ──── N operation_log
```

**表清单**

| 表名 | 说明 | 预估量级 |
|---|---|---|
| `sys_user` | 系统用户（学生/财务/管理员） | 万级 |
| `student` | 学籍扩展信息 | 万级 |
| `payment_item` | 缴费项目 | 千级 |
| `payment_order` | 缴费订单 | 十万级/年 |
| `refund_record` | 退款记录 | 千级 |
| `reconciliation_batch` | 对账批次 | 百级/年 |
| `reconciliation_detail` | 对账明细 | 十万级/年 |
| `notice` / `notice_read` | 通知 / 已读 | 千级 / 百万级 |
| `operation_log` | 操作日志 | 百万级（按月归档） |

**统一字段约定**：`id BIGINT UNSIGNED AUTO_INCREMENT`、`created_at`/`updated_at DATETIME`、`deleted TINYINT`（逻辑删除）、金额 `DECIMAL(10,2)`、引擎 `InnoDB`、字符集 `utf8mb4`。

### 7.2 建表脚本 `backend/src/main/resources/db/schema.sql`

```sql
-- ============================================================
-- 大学生缴费管理系统 建表脚本
-- MySQL 8.0+ / utf8mb4
-- 执行方式见 7.5
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
    target_scope  VARCHAR(20)     NOT NULL DEFAULT 'ALL' COMMENT 'ALL/COMPOSITE，GRADE/COLLEGE/MAJOR/CUSTOM 为历史值',
    scope_value   VARCHAR(1000)            DEFAULT NULL COMMENT '适用范围：COMPOSITE 存四维对象，历史为单维度数组',
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
    channel         VARCHAR(20)     NOT NULL DEFAULT 'MOCK' COMMENT '支付渠道，当前仅实现 MOCK',
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
```

### 7.3 初始化数据 `backend/src/main/resources/db/data.sql`

```sql
USE campus_payment;

-- 密码统一为 123456（BCrypt 强度 10）
-- 若登录报“用户名或密码错误”，用 7.4 的程序重新生成哈希后替换
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
```

### 7.4 生成 BCrypt 密码哈希（备用）

新建临时类 `backend/src/test/java/com/campus/payment/PasswordHashGenerator.java`：

```java
package com.campus.payment;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "123456";
        System.out.println(new BCryptPasswordEncoder().encode(raw));
    }
}
```

```bash
cd backend
mvn -q test-compile exec:java -Dexec.mainClass=com.campus.payment.PasswordHashGenerator -Dexec.args=123456
```

把输出的 `$2a$10$...` 替换 `data.sql` 中的 `@pwd` 后重新执行 `data.sql`。

### 7.5 执行初始化（两种方式）

**方式 A：手动执行 SQL**（推荐首次）

```bash
# 1) 建表（脚本内含 DROP/CREATE DATABASE，首次执行即可）
mysql -u root -p < backend/src/main/resources/db/schema.sql

# 2) 初始化数据
mysql -u root -p campus_payment < backend/src/main/resources/db/data.sql
```

> **Windows PowerShell 不支持 `<` 输入重定向**，请改用：
> ```powershell
> cmd /c "mysql -u root -p123456 < backend/src/main/resources/db/schema.sql"
> cmd /c "mysql -u root -p123456 < backend/src/main/resources/db/data.sql"
> ```
> 或 `Get-Content backend/src/main/resources/db/schema.sql -Raw | mysql -u root -p123456`。

**方式 B：Spring Boot 启动自动执行**（仅开发环境）

在 `application-dev.yml` 中开启（**生产环境必须关闭**）：

```yaml
spring:
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql
      data-locations: classpath:db/data.sql
      continue-on-error: false
```

> 注意：`schema.sql` 含 `DROP DATABASE`，`mode: always` 时**每次启动都会清库**。若想保留数据，请删掉脚本里的 `DROP DATABASE` 与 `USE` 语句，并把 `mode` 改为 `never`，改为手动执行。

**验证初始化结果**

```bash
mysql -u root -p campus_payment -e "SHOW TABLES; SELECT username, role FROM sys_user; SELECT item_no, status FROM payment_item;"
```

预期：10 张表；4 个用户（1 ADMIN + 1 FINANCE + 2 STUDENT）；2 个 `PUBLISHED` 项目。

### 7.6 演示数据一键重置（开发/答辩用）

`db/demo-data.sql` 在基础种子之上补了一套可直接演示的业务数据：10 名学生（3 个学院、2 个年级）、5 个不同状态的项目、32 笔订单（已缴 / 待支付 / 已关闭 / 部分退款 / 全额退款）、2 条退款记录、1 个已完成的对账批次（含渠道多账与金额差异两类差异）、5 条通知（含 1 条草稿）。

推荐用脚本执行，它会**先备份、再重建、最后打印财务口径校验**：

```bash
# 备份 + 重建库表 + 灌入演示数据（需要 pymysql：pip install pymysql）
python scripts/reset_demo_data.py

# 仅清空业务数据后重灌，保留表结构
python scripts/reset_demo_data.py --data-only

# 不备份（谨慎）
python scripts/reset_demo_data.py --no-backup
```

脚本会顺带用库中真实订单号生成 `docs/samples/对账账单-MOCK-<日期>.csv`，可直接在「对账管理 → 上传账单」中演示匹配（含 1 笔渠道多账、1 笔金额差异）。备份产物写入 `backups/`（已在 `.gitignore` 中排除）。

灌入后的预期口径：**应收 64560.00 / 实收 57320.00 / 欠费 7240.00 / 缴费率 88.79%**，订单 32 笔（已付 27 笔），涉及 10 名学生。

演示账号（密码均为 `123456`）：

| 账号 | 角色 | 场景 |
|---|---|---|
| `admin` | 管理员 | 操作日志、用户管理、系统设置、数据字典 |
| `caiwu01` | 财务 | 看板/项目/订单/对账/报表/通知全流程 |
| `20230101` | 学生 | 李小明，4 个项目全部缴清 |
| `20230102` | 学生 | 王小红，住宿费有 600 元部分退款 |
| `20240102` | 学生 | 林晓彤，学费 5800 元待支付（可演示收银台与选渠道） |
| `20240201` | 学生 | 黄子轩，教材费下单后已关闭，无待缴订单 |

---

## 8. 后端启动

### 8.1 主配置 `backend/src/main/resources/application.yml`

```yaml
server:
  port: 8080
  servlet:
    context-path: /api          # 所有接口前缀，最终访问形如 http://localhost:8080/api/v1/...
  tomcat:
    uri-encoding: UTF-8

spring:
  application:
    name: campus-payment
  profiles:
    active: dev
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: Asia/Shanghai
    default-property-inclusion: non_null
  servlet:
    multipart:
      max-file-size: 20MB       # 对账账单文件上传
      max-request-size: 20MB

mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-aliases-package: com.campus.payment.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: false
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# 业务自定义配置
jwt:
  secret: campus-payment-secret-key-please-change-in-prod-32bytes-min
  access-expire-seconds: 7200      # 访问令牌 2 小时
  refresh-expire-seconds: 604800   # 刷新令牌 7 天

payment:
  order-expire-minutes: 30         # 未支付订单超时时间
  default-channel: MOCK            # 当前仅实现模拟支付渠道

springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.campus.payment: debug
```

### 8.2 开发环境 `backend/src/main/resources/application-dev.yml`

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/campus_payment?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:123456}
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
  # Redis 可选，未部署则保持注释
  # data:
  #   redis:
  #     host: localhost
  #     port: 6379
  #     database: 0
```

> 连接串四个参数缺一不可：`characterEncoding=utf8`（防中文乱码）、`serverTimezone=Asia/Shanghai`（防时区偏移 8 小时）、`useSSL=false`、`allowPublicKeyRetrieval=true`（MySQL 8 本地连接必加）。

### 8.3 启动命令

```bash
cd backend
```

**方式 A：Maven Wrapper（推荐，无需本地装 Maven）**

```bash
# macOS / Linux
./mvnw spring-boot:run
# Windows PowerShell / CMD
.\mvnw.cmd spring-boot:run
```

**方式 B：本地 Maven**

```bash
mvn clean spring-boot:run
```

**方式 C：打包后运行（更接近生产）**

```bash
mvn clean package -DskipTests
java -jar target/campus-payment-1.0.0.jar --spring.profiles.active=dev
```

**启动成功标志**

```
Started PaymentApplication in 4.235 seconds (process running for 5.118)
Tomcat started on port(s): 8080 (http) with context path '/api'
```

**快速自检**

```bash
curl http://localhost:8080/api/actuator/health
# 期望：{"status":"UP"}
```

> Windows PowerShell 中 `curl` 是 `Invoke-WebRequest` 的别名，行为不同。请统一使用 `curl.exe`：
> ```powershell
> curl.exe http://localhost:8080/api/actuator/health
> ```

---

## 9. 前端启动

### 9.1 环境变量

`frontend/.env.development`

```
VITE_APP_TITLE=大学生缴费管理系统
VITE_API_BASE_URL=/api
VITE_PORT=5173
```

`frontend/.env.production`

```
VITE_APP_TITLE=大学生缴费管理系统
VITE_API_BASE_URL=/api
```

### 9.2 `frontend/vite.config.ts`

```ts
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [
      vue(),
      AutoImport({ resolvers: [ElementPlusResolver()] }),
      Components({ resolvers: [ElementPlusResolver()] })
    ],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
      port: Number(env.VITE_PORT) || 5173,
      host: '0.0.0.0',
      open: true,
      proxy: {
        // 关键：把 /api 转发到后端，规避开发期跨域
        '/api': {
          target: 'http://localhost:8080',
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      chunkSizeWarningLimit: 1500
    }
  }
})
```

### 9.3 启动命令

```bash
cd frontend
npm install          # 首次或 package.json 变更后执行
npm run dev          # 开发模式，默认 http://localhost:5173
```

**生产构建与本地预览**

```bash
npm run build        # 产物输出到 frontend/dist
npm run preview      # 本地预览构建产物，端口 4173
```

**若使用 pnpm**

```bash
npm install -g pnpm
pnpm install
pnpm dev
```

---

## 10. 接口约定

### 10.1 基础约定

| 项 | 约定 |
|---|---|
| Base URL | 开发 `http://localhost:8080/api`，前端经 Vite 代理访问 `/api` |
| 版本前缀 | `/v1`，如 `/api/v1/payment-items` |
| 认证方式 | `Authorization: Bearer <accessToken>` |
| 请求体格式 | `Content-Type: application/json; charset=UTF-8` |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`（字符串） |
| **金额格式** | **字符串，两位小数**，如 `"5800.00"`；响应与请求均不使用浮点数 |
| 分页入参 | `pageNum`（默认 1）、`pageSize`（默认 10，上限 100） |
| 幂等 | 下单接口必须带 `Idempotency-Key: <uuid>` |
| 排序 | `sort=createdAt,desc` |

### 10.2 统一响应体

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "a1b2c3d4e5f6",
  "timestamp": 1760832000000
}
```

分页响应 `data` 结构：

```json
{
  "list": [],
  "total": 128,
  "pageNum": 1,
  "pageSize": 10
}
```

### 10.3 错误码规范

| 码段 | 含义 | 示例 |
|---|---|---|
| `0` | 成功 | — |
| `400xx` | 参数/校验错误 | `40001 参数校验失败`、`40002 缺少幂等键` |
| `401xx` | 未认证 | `40101 令牌缺失`、`40102 令牌过期`、`40103 用户名或密码错误` |
| `403xx` | 无权限 | `40301 无权访问该数据`、`40302 角色无此操作权限` |
| `404xx` | 资源不存在 | `40401 订单不存在`、`40402 缴费项目不存在` |
| `600xx` | 业务规则冲突 | `60001 该项目不在缴费时间窗内`、`60002 订单状态不允许此操作`、`60003 重复支付`、`60004 已缴清无需再缴`、`60005 退款金额超出实缴` |
| `500xx` | 服务端异常 | `50000 系统内部错误` |

前端拦截器按码段处理：`401xx` → 清 token 跳登录；`600xx` → `ElMessage.warning` 直接展示 `message`；`500xx` → 统一提示并上报 `traceId`。

### 10.4 接口清单（MVP）

**认证**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/v1/auth/login` | 登录，返回 access/refresh token | 匿名 |
| POST | `/v1/auth/refresh` | 刷新 access token | 匿名（带 refreshToken） |
| POST | `/v1/auth/logout` | 登出（token 加入黑名单） | 登录 |
| GET | `/v1/auth/me` | 当前用户信息与角色 | 登录 |
| PUT | `/v1/auth/password` | 修改本人密码 | 登录 |

**缴费项目**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/v1/payment-items` | 分页查询项目（财务视角） | FINANCE |
| GET | `/v1/payment-items/available` | 当前学生可缴项目 | STUDENT |
| GET | `/v1/payment-items/scope-options` | 适用范围下拉项（年级/学院/专业，取自学籍档案） | FINANCE |
| GET | `/v1/payment-items/scope-students` | 指定名单候选学生（`keyword` 搜索 / `ids` 回显） | FINANCE |
| POST | `/v1/payment-items/scope-preview` | 适用范围覆盖人数预览（发布前确认能覆盖到学生） | FINANCE |
| GET | `/v1/payment-items/{id}` | 项目详情 | STUDENT / FINANCE |
| GET | `/v1/payment-items/{id}/close-preview` | 下架前影响预览 | FINANCE |
| POST | `/v1/payment-items` | 新建项目（草稿） | FINANCE |
| PUT | `/v1/payment-items/{id}` | 修改（仅 DRAFT） | FINANCE |
| POST | `/v1/payment-items/{id}/publish` | 发布 | FINANCE |
| POST | `/v1/payment-items/{id}/close` | 下架（存在未完成订单时拒绝） | FINANCE |

**订单与支付**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/v1/orders` | 创建订单（需 `Idempotency-Key`；分次项目可传本次 `amount`） | STUDENT |
| GET | `/v1/orders` | 订单分页（学生自动过滤本人，财务可查全部） | STUDENT / FINANCE |
| GET | `/v1/orders/channels` | 收银台可选支付渠道 | 登录 |
| GET | `/v1/orders/{orderNo}` | 订单详情 | 本人 / FINANCE |
| POST | `/v1/orders/{orderNo}/pay` | 发起支付，返回支付参数或收银台地址 | STUDENT |
| POST | `/v1/orders/{orderNo}/close` | 关闭未支付订单 | 本人 |
| POST | `/v1/pay/callback/{channel}` | 渠道异步回调（验签） | 匿名 |
| POST | `/v1/orders/{orderNo}/refund` | 退款 | FINANCE |
| GET | `/v1/orders/{orderNo}/voucher` | 下载电子缴费凭证（PDF） | 本人 / FINANCE |

**对账**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/v1/reconciliations` | 对账批次分页 | FINANCE |
| POST | `/v1/reconciliations` | 创建批次并上传账单 CSV | FINANCE |
| POST | `/v1/reconciliations/{batchNo}/match` | 执行自动匹配 | FINANCE |
| GET | `/v1/reconciliations/{batchNo}/details` | 差异明细分页（`diffType` 筛选） | FINANCE |
| PUT | `/v1/reconciliations/details/{id}/handle` | 人工核销 | FINANCE |

**统计**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/v1/stats/overview` | 总览：应收/实收/欠费率/笔数 | FINANCE |
| GET | `/v1/stats/by-item` | 按项目统计 | FINANCE |
| GET | `/v1/stats/by-college` | 按学院统计 | FINANCE |
| GET | `/v1/stats/trend` | 按日/月趋势 | FINANCE |
| GET | `/v1/stats/export` | 导出 Excel（EasyExcel 流式） | FINANCE |

**通知**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/v1/notices` | 通知列表（按角色过滤） | 登录 |
| GET | `/v1/notices/{id}` | 通知详情（学生端自动标记已读） | 登录 |
| GET | `/v1/notices/unread-count` | 未读数 | 登录 |
| POST | `/v1/notices` | 创建通知 | FINANCE |
| POST | `/v1/notices/{id}/publish` | 发布通知 | FINANCE |

**系统管理**

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/v1/users` | 用户分页（支持角色/关键字） | ADMIN |
| POST | `/v1/users` | 新增用户 | ADMIN |
| PUT | `/v1/users/{id}` | 编辑用户 | ADMIN |
| PUT | `/v1/users/{id}/status` | 启用/禁用 | ADMIN |
| PUT | `/v1/users/{id}/reset-password` | 重置密码 | ADMIN |
| POST | `/v1/students/import` | 学生名单 CSV 导入 | ADMIN |
| GET | `/v1/logs` | 操作日志分页 | ADMIN |
| GET | `/v1/system/info` | 系统运行信息（环境/账号规模/渠道状态/参数） | ADMIN |

### 10.5 接口示例

**登录**

```bash
curl.exe -X POST "http://localhost:8080/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"20230101\",\"password\":\"123456\"}"
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "expiresIn": 7200,
    "user": { "id": 3, "username": "20230101", "realName": "李小明", "role": "STUDENT" }
  },
  "traceId": "f3a91c2b7d40",
  "timestamp": 1760832000000
}
```

**查询可缴项目**

```bash
curl.exe "http://localhost:8080/api/v1/payment-items/available?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer <accessToken>"
```

**创建订单**

```bash
curl.exe -X POST "http://localhost:8080/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <accessToken>" \
  -H "Idempotency-Key: 7c1a2f30-9b84-4b1e-8f77-2d0c5a91b3e6" \
  -d "{\"itemId\":1,\"channel\":\"MOCK\"}"
```

响应 `data`：

```json
{
  "orderNo": "20261019094212008371",
  "amount": "5800.00",
  "status": "PENDING",
  "expireAt": "2026-10-19 10:12:12",
  "payUrl": "http://localhost:5173/#/student/cashier?orderNo=20261019094212008371"
}
```

**模拟支付回调**（MVP 用 `MOCK` 渠道，便于联调与演示）

```bash
curl.exe -X POST "http://localhost:8080/api/v1/pay/callback/mock" \
  -H "Content-Type: application/json" \
  -d "{\"orderNo\":\"20261019094212008371\",\"transactionNo\":\"MOCK20261019094212\",\"amount\":\"5800.00\",\"status\":\"SUCCESS\"}"
```

**查询订单**

```bash
curl.exe "http://localhost:8080/api/v1/orders/20261019094212008371" \
  -H "Authorization: Bearer <accessToken>"
```

### 10.6 接口文档

启动后端后访问 Swagger UI：

```
http://localhost:8080/api/swagger-ui.html
```

OpenAPI JSON：`http://localhost:8080/api/v3/api-docs`

新增接口时**必须**补 `@Operation(summary = "...")` 与 `@Schema` 注解，保证文档可读。

---

## 11. 运行与验证

### 11.1 启动顺序

```
1. 启动 MySQL（本地服务或 docker 容器）
2. 初始化数据库（schema.sql → data.sql）
3. 启动后端（8080）→ 等待 "Started PaymentApplication"
4. 启动前端（5173）→ 浏览器自动打开
```

### 11.2 冒烟测试用例（按顺序执行，全部通过视为环境 OK）

| # | 场景 | 操作 | 预期结果 |
|---|---|---|---|
| 1 | 健康检查 | 访问 `/api/actuator/health` | `{"status":"UP"}` |
| 2 | 学生登录 | `20230101` / `123456` | 跳转学生首页，右上角显示「李小明」 |
| 3 | 可缴项目 | 学生端「缴费项目」 | 列出 2 个 PUBLISHED 项目，含金额与截止时间 |
| 4 | 下单 | 点击「立即缴费」 | 生成订单，跳转收银台，显示待支付与倒计时 |
| 5 | 支付 | 收银台点「模拟支付成功」 | 订单状态变 `PAID`，页面显示缴费成功 |
| 6 | 记录查询 | 学生端「我的订单」 | 出现该笔订单，状态「已支付」，可下载凭证 |
| 7 | 数据权限 | 用 `20230102` 登录查订单 | **看不到** `20230101` 的订单 |
| 8 | 财务登录 | `caiwu01` / `123456` | 进入财务看板，可看到全部订单 |
| 9 | 项目发布 | 财务端新建项目 → 发布 | 学生端刷新后可见新项目 |
| 10 | 对账 | 上传账单 CSV → 匹配 | 批次显示匹配笔数与差异笔数 |
| 11 | 报表 | 财务端「统计报表」 | 图表有数据，导出 Excel 可打开 |
| 12 | 管理员 | `admin` / `123456` | 可进入用户管理，禁用某学生后该学生无法登录 |
| 13 | 越权校验 | 学生用 Postman 调 `/v1/users` | 返回 `40302 角色无此操作权限` |
| 14 | 幂等 | 同一 `Idempotency-Key` 连续下单两次 | 两次返回**同一个** `orderNo` |
| 15 | 关闭订单 | 关闭 PENDING 订单后再次支付 | 返回 `60002 订单状态不允许此操作` |

### 11.3 前端页面路由约定

| 路由 | 页面 | 角色 |
|---|---|---|
| `/login` | 登录 | 匿名 |
| `/student/home` | 学生首页 | STUDENT |
| `/student/items` | 可缴项目 | STUDENT |
| `/student/cashier?orderNo=` | 收银台 | STUDENT |
| `/student/orders` | 我的订单 | STUDENT |
| `/finance/dashboard` | 财务看板 | FINANCE / ADMIN |
| `/finance/items` | 项目管理 | FINANCE / ADMIN |
| `/finance/orders` | 订单管理 | FINANCE / ADMIN |
| `/finance/reconciliation` | 对账管理 | FINANCE / ADMIN |
| `/finance/report` | 统计报表 | FINANCE / ADMIN |
| `/finance/notices` | 通知管理 | FINANCE / ADMIN |
| `/admin/users` | 用户管理 | ADMIN |
| `/admin/logs` | 操作日志 | ADMIN |

路由守卫逻辑：无 token → 重定向 `/login?redirect=...`；有 token 但角色不匹配 → 跳 `/403`。

### 11.4 打包部署（简版，供后期参考）

```bash
# 前端
cd frontend && npm run build          # 产物 frontend/dist

# 后端
cd backend && mvn clean package -DskipTests   # 产物 target/campus-payment-1.0.0.jar

# 运行
java -jar campus-payment-1.0.0.jar --spring.profiles.active=prod
```

Nginx 关键配置（前后端同源，彻底规避跨域）：

```nginx
server {
    listen 80;
    server_name payment.example.edu.cn;

    root /var/www/campus-payment/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;   # history 模式必须，否则刷新 404
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
        client_max_body_size 20m;
    }
}
```

---

## 12. 开发规范

### 12.1 Git 分支与提交

```
main        生产分支，仅允许通过 release 合并，每次合并打 tag
develop     集成分支，功能开发完成后合入
feature/*   功能分支，如 feature/payment-item-crud
fix/*       缺陷修复，如 fix/order-status-stuck
```

提交信息格式：`<type>(<scope>): <subject>`

```
feat(order): 支持订单超时自动关闭
fix(auth): 修复令牌过期后未跳转登录页
docs(readme): 补充对账流程图
refactor(item): 抽取适用范围匹配逻辑
```

### 12.2 后端硬性要求

1. **金额禁用 `double`/`float`**，统一 `BigDecimal`，比较用 `compareTo`；
2. **所有写操作加 `@Transactional(rollbackFor = Exception.class)`**；
3. 接口入参一律用 DTO + `@Valid`，**禁止直接用 Entity 接参**；
4. 出参一律 VO，`password` 字段永不返回；
5. 分页必须走 MyBatis-Plus 分页插件，**禁止在内存中分页**；
6. 敏感操作（发布、退款、对账核销、用户禁用）写 `operation_log`；
7. 全局异常由 `GlobalExceptionHandler` 统一捕获，`BusinessException` 携带 `ResultCode`。

### 12.3 前端硬性要求

1. 接口调用统一走 `src/api/*.ts`，**禁止在组件内直接 `axios`**；
2. 金额展示统一用 `utils/format.ts` 的 `formatAmount()`，保留两位小数加千分位；
3. 列表页必须处理 `loading` / `empty` / `error` 三态；
4. 按钮级权限用 `v-permission` 指令，仅作展示控制，**后端必须再校验**；
5. 提交类按钮加 `:loading` 防重复点击，下单额外带幂等键。

---

## 13. 常见问题 FAQ

### Q1 启动报 `UnsupportedClassVersionError: ... class file version 61.0`

JDK 版本低于 17（61.0 对应 Java 17）。执行 `java -version` 确认，切换 `JAVA_HOME` 后**重开终端**。

### Q2 端口被占用（8080 / 5173 / 3306）

```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F
# macOS / Linux
lsof -i:8080
kill -9 <PID>
```

临时改端口：后端改 `server.port`，前端改 `.env.development` 的 `VITE_PORT`（同时更新 `vite.config.ts` 的代理 target）。

### Q3 连接数据库报 `Public Key Retrieval is not allowed`

MySQL 8 默认 `caching_sha2_password`，JDBC 连接串必须加 `allowPublicKeyRetrieval=true`（已在 8.2 配置）。

### Q4 查询时间比实际少 8 小时

三层都要对齐 `Asia/Shanghai`：JDBC `serverTimezone=Asia/Shanghai`、`spring.jackson.time-zone`、MySQL 容器 `-e TZ=Asia/Shanghai`（或 `default-time-zone=+08:00`）。

### Q5 中文写入数据库变 `???`

1. 库/表字符集必须是 `utf8mb4`（`SHOW CREATE TABLE payment_item;` 检查）；
2. 连接串带 `characterEncoding=utf8`；
3. 客户端 `SET NAMES utf8mb4;`。

### Q6 前端 401 频繁出现，刷新后登录态丢失

- 确认请求头为 `Authorization: Bearer <token>`（注意 Bearer 后**一个空格**）；
- 检查 token 是否存 `localStorage` 且刷新页面后能读到；
- 若服务器与本地时间偏差超过 token 有效期，会误判过期，校准系统时间。

### Q7 前端跨域 `has been blocked by CORS policy`

开发环境**不要**在后端加 `@CrossOrigin`，直接用 Vite 代理（9.2 已配置）。若代理无效，检查 `vite.config.ts` 修改后是否**重启了 dev server**。

### Q8 `npm install` 卡住或超时

```bash
npm config set registry https://registry.npmmirror.com
npm cache clean --force
rm -rf node_modules package-lock.json   # Windows: rmdir /s /q node_modules & del package-lock.json
npm install
```

### Q9 Maven 依赖下载极慢

按 6.3 配置阿里云镜像后执行 `mvn -U clean install`。首次构建预计 3–8 分钟，属正常。

### Q10 Lombok 报错「找不到 getter/setter」

IDEA：`Settings → Build → Compiler → Annotation Processors → Enable annotation processing` 勾选，并安装 Lombok 插件。若仍报错，检查 `pom.xml` 中 Lombok 是否被 `<optional>true</optional>` 以外的 scope 限制。

### Q11 前端打包后刷新页面 404

Vue Router 使用 history 模式，Nginx 必须配置 `try_files $uri $uri/ /index.html;`（见 11.4）。临时方案：改用 `createWebHashHistory()`。

### Q12 支付回调重复执行导致重复入账

必须在回调处理里做两件事：`transaction_no` 唯一索引兜底 + `UPDATE payment_order SET status='PAID' WHERE order_no=? AND status='PAYING'`，判断影响行数是否为 1。仅靠「先查后改」在并发下会失效。

### Q13 账单 CSV 上传后无数据

检查文件编码（必须 UTF-8，Excel 另存的 CSV 常为 GBK）、表头列名是否与解析器约定一致、金额列是否带千分位逗号。解析失败时接口应返回明确的 `40001` 与行号。

### Q14 单元测试连不上数据库

测试用 H2 或独立库 `campus_payment_test`，在 `src/test/resources/application-test.yml` 单独配置，**不要**让测试污染开发库。

---

## 14. 后续开发计划

### 14.1 阶段一：MVP 打通（4 周）

| 周 | 目标 | 交付物 |
|---|---|---|
| W1 | 环境骨架 + 认证 + 数据库 | 前后端可启动，三角色可登录，`/auth/me` 可用 |
| W2 | 缴费项目 CRUD + 学生可缴列表 | 财务可发布项目，学生可见 |
| W3 | 下单 → 支付 → 回调 → 记录查询 | 模拟支付全链路闭环，订单状态机生效 |
| W4 | 联调 + 冒烟测试 + 文档 | 11.2 全部用例通过，README 定稿 |

### 14.2 阶段二：财务能力补齐（3 周）

- 对账模块：账单导入、自动匹配、差异明细、人工核销；
- 统计报表：总览看板、按项目/学院/时间维度、Excel 导出；
- 通知提醒：通知发布、站内信、截止前自动提醒、欠费名单导出；
- 退款流程：申请 → 审核 → 渠道退款 → 状态回写；
- 电子缴费凭证 PDF 生成。

### 14.3 阶段三：扩展期（可选）

| 方向 | 说明 |
|---|---|
| 支付渠道 | 当前仅实现模拟支付渠道（不接真实商户号）；如需接入微信/支付宝，新增 `PaymentChannelAdapter` 实现并补充 `PayChannel` 枚举值即可，无需改动订单与退款逻辑 |
| 权限模型 | 引入 `sys_role` / `sys_permission` / `role_permission`，支持细粒度 RBAC 与按学院数据范围 |
| 性能 | Redis 缓存项目与字典；热点统计走预聚合表；订单表按月分区 |
| 可观测性 | 接入 Prometheus + Grafana，接口耗时与错误率告警 |
| 移动端 | 微信小程序或 H5 适配，扫码缴费 |
| 教务对接 | 与学籍系统做学生名单增量同步，替代 CSV 导入 |
| 电子票据 | 对接财政电子票据平台，缴费成功自动开票 |
| 部署 | Docker Compose 一键起全套；CI/CD 自动化构建与灰度发布 |

### 14.4 已知技术债（需在阶段二清除）

1. `payment_item.scope_value` 用 JSON 字符串存储，无法走索引；数据量增大后应拆 `payment_item_scope` 关联表；
2. 通知推送目前仅站内信，短信/邮件接口留空实现；
3. 统计接口为实时聚合 SQL，超过 50 万订单需改为日汇总表；
4. 操作日志未做归档，需补按月分表或定期清理任务。

---

## 附：一键启动速查（首次跑通全流程）

```bash
# ① 起数据库
docker run -d --name campus-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 -e TZ=Asia/Shanghai \
  mysql:8.0.36 --character-set-server=utf8mb4 --default-time-zone=+08:00

# ② 初始化
mysql -u root -p123456 < backend/src/main/resources/db/schema.sql
mysql -u root -p123456 campus_payment < backend/src/main/resources/db/data.sql

# ③ 起后端（新终端）
cd backend && mvn clean spring-boot:run

# ④ 起前端（再开一个终端）
cd frontend && npm install && npm run dev

# ⑤ 浏览器打开 http://localhost:5173
#    admin/123456 · caiwu01/123456 · 20230101/123456
```

> 生产环境上线前必改项：数据库密码、`jwt.secret`、`spring.sql.init.mode`（必须为 `never`）、`logging.level`（去掉 SQL 打印）、支付渠道密钥改为环境变量注入。

---

**文档维护**：需求变更、接口调整、表结构修改必须同步更新本 README，并在 PR 描述中注明变更章节。
