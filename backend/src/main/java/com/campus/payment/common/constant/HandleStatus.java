package com.campus.payment.common.constant;

/**
 * 对账明细处理状态。
 */
public enum HandleStatus {
    UNHANDLED,
    HANDLED,
    IGNORED;

    public static HandleStatus of(String value) {
        for (HandleStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
