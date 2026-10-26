package com.campus.payment.common.constant;

/**
 * 角色（README 2.1，单角色模型）。
 * 职责边界：STUDENT 只办本人业务；FINANCE 负责缴费项目、订单、退款、对账与通知；
 * ADMIN 只做账号维护、字典与操作日志等系统事务，不接触任何财务数据。
 */
public enum Role {
    STUDENT,
    FINANCE,
    ADMIN;

    public static Role of(String value) {
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }
}
