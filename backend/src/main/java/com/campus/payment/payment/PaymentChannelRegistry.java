package com.campus.payment.payment;

import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 渠道注册表：Spring 注入所有适配器实现，按 channel 路由。
 */
@Component
public class PaymentChannelRegistry {

    private final Map<String, PaymentChannelAdapter> adapters = new HashMap<>();

    public PaymentChannelRegistry(List<PaymentChannelAdapter> adapterList) {
        for (PaymentChannelAdapter adapter : adapterList) {
            adapters.put(adapter.channel().toUpperCase(Locale.ROOT), adapter);
        }
    }

    public PaymentChannelAdapter get(String channel) {
        PaymentChannelAdapter adapter = channel == null ? null : adapters.get(channel.toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "不支持的支付渠道：" + channel);
        }
        return adapter;
    }

    public boolean supports(String channel) {
        return channel != null && adapters.containsKey(channel.toUpperCase(Locale.ROOT));
    }
}
