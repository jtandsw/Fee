package com.campus.payment.common.constant;

/**
 * 支付渠道（README 3.2（2）订单表 channel 字段）。
 */
public enum PayChannel {
    MOCK,
    WECHAT,
    ALIPAY,
    UNIONPAY,
    CASH;

    public static PayChannel of(String value) {
        for (PayChannel channel : values()) {
            if (channel.name().equalsIgnoreCase(value)) {
                return channel;
            }
        }
        return null;
    }
}
