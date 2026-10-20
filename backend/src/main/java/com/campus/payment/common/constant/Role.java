package com.campus.payment.common.constant;

/**
 * 角色（README 2.1，单角色模型）。
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

    public boolean isFinanceOrAdmin() {
        return this == FINANCE || this == ADMIN;
    }
}
