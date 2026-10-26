package com.campus.payment.common.constant;

/**
 * 支付渠道（对应 payment_order.channel 字段）。
 * <p>
 * 当前系统只提供模拟支付渠道：演示环境不接真实商户，也不需要商户证书。
 * 若要接入真实渠道（微信 / 支付宝 / 银联），按以下两步扩展即可：
 * <ol>
 *   <li>在此枚举增加一个编码，例如 {@code WECHAT}；</li>
 *   <li>新增一个 {@code PaymentChannelAdapter} 实现类并标注 {@code @Component}，
 *       支付渠道注册表会自动发现它，收银台随即可选。</li>
 * </ol>
 * 未实现适配器的渠道不会出现在候选列表中，界面上不会出现「未接入」的占位项。
 */
public enum PayChannel {
    MOCK;

    public static PayChannel of(String value) {
        if (value == null) {
            return null;
        }
        for (PayChannel channel : values()) {
            if (channel.name().equalsIgnoreCase(value)) {
                return channel;
            }
        }
        return null;
    }
}
