package com.campus.payment.common.constant;

/**
 * 缴费项目状态（README 3.2（1））：DRAFT → PUBLISHED → CLOSED，CLOSED 不可回退。
 */
public enum ItemStatus {
    DRAFT,
    PUBLISHED,
    CLOSED;

    public static ItemStatus of(String value) {
        for (ItemStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
