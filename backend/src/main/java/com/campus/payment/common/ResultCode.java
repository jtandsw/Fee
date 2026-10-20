package com.campus.payment.common;

import lombok.Getter;

/**
 * 错误码（README 10.3）。
 * 400xx 参数、401xx 未认证、403xx 无权限、404xx 不存在、600xx 业务冲突、500xx 服务端。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "ok"),

    PARAM_INVALID(40001, "参数校验失败"),
    IDEMPOTENCY_KEY_MISSING(40002, "缺少幂等键"),
    BILL_FILE_INVALID(40003, "账单文件格式不正确"),

    UNAUTHORIZED(40101, "令牌缺失或无效"),
    TOKEN_EXPIRED(40102, "令牌已过期"),
    LOGIN_FAILED(40103, "用户名或密码错误"),
    ACCOUNT_DISABLED(40104, "账号已被禁用"),

    FORBIDDEN_DATA(40301, "无权访问该数据"),
    FORBIDDEN_ROLE(40302, "角色无此操作权限"),

    ORDER_NOT_FOUND(40401, "订单不存在"),
    ITEM_NOT_FOUND(40402, "缴费项目不存在"),
    USER_NOT_FOUND(40403, "用户不存在"),
    NOTICE_NOT_FOUND(40404, "通知不存在"),
    BATCH_NOT_FOUND(40405, "对账批次不存在"),
    DETAIL_NOT_FOUND(40406, "对账明细不存在"),

    ITEM_NOT_IN_WINDOW(60001, "该项目不在缴费时间窗内"),
    ORDER_STATUS_INVALID(60002, "订单状态不允许此操作"),
    DUPLICATE_PAY(60003, "重复支付"),
    ALREADY_PAID(60004, "已缴清无需再缴"),
    REFUND_EXCEED(60005, "退款金额超出实缴"),
    ITEM_STATUS_INVALID(60006, "项目状态不允许此操作"),
    BATCH_STATUS_INVALID(60007, "对账批次状态不允许此操作"),
    USERNAME_EXISTS(60008, "登录账号已存在"),
    BATCH_EXISTS(60009, "该渠道该日期的对账批次已存在"),
    DATA_CONFLICT(60010, "数据已存在，请重试"),

    INTERNAL_ERROR(50000, "系统内部错误"),
    CHANNEL_NOT_SUPPORTED(50001, "支付渠道暂未接入");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
