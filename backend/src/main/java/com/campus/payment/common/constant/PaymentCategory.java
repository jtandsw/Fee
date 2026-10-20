package com.campus.payment.common.constant;

/**
 * 缴费类别（README 3.2（1）），同时用作项目编号的类别码前缀。
 */
public enum PaymentCategory {
    TUITION("TX"),
    ACCOMMODATION("SFF"),
    BOOK("JC"),
    EXAM("KS"),
    OTHER("QT");

    private final String prefix;

    PaymentCategory(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() {
        return prefix;
    }

    public static PaymentCategory of(String value) {
        for (PaymentCategory category : values()) {
            if (category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        return null;
    }
}
