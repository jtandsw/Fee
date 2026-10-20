package com.campus.payment.common.constant;

/**
 * 订单状态机（README 3.2（2））：PENDING → PAYING → PAID → REFUNDED / PARTIAL_REFUND，
 * PENDING/PAYING 可关闭，PAYING 支付失败可回退 PENDING。
 */
public enum OrderStatus {
    PENDING,
    PAYING,
    PAID,
    CLOSED,
    REFUNDED,
    PARTIAL_REFUND;

    public static OrderStatus of(String value) {
        for (OrderStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    public boolean isPaid() {
        return this == PAID || this == PARTIAL_REFUND;
    }

    public boolean isClosable() {
        return this == PENDING || this == PAYING;
    }
}
