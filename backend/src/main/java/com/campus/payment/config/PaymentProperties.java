package com.campus.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付业务配置（README 8.1 payment 段）。
 */
@Data
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** 未支付订单超时分钟数 */
    private long orderExpireMinutes = 30;

    /** 默认支付渠道 */
    private String defaultChannel = "MOCK";

    private Wechat wechat = new Wechat();

    @Data
    public static class Wechat {
        private String appId;
        private String mchId;
        private String apiKey;
        private String notifyUrl;
    }
}
