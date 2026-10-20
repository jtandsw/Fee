package com.campus.payment.payment;

import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import com.campus.payment.config.PaymentProperties;
import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.vo.PayVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 微信支付适配器占位实现（README 4.1 / 14.3）。
 * <p>
 * MVP 未引入微信支付 SDK，且未配置商户证书，因此：
 * 未配置 app-id/mch-id 时直接返回 50001；即使配置了也不会发起真实下单，
 * 正式接入时在 createPayment / parseCallback / refund 内补 JSAPI 下单、验签与退款 API 调用。
 */
@Component
@RequiredArgsConstructor
public class WechatChannelAdapter implements PaymentChannelAdapter {

    private final PaymentProperties paymentProperties;

    @Override
    public String channel() {
        return "WECHAT";
    }

    @Override
    public PayVO createPayment(PaymentOrder order) {
        PaymentProperties.Wechat wechat = paymentProperties.getWechat();
        if (isBlank(wechat.getAppId()) || isBlank(wechat.getMchId()) || isBlank(wechat.getApiKey())) {
            throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "微信支付渠道未配置商户参数，请使用 MOCK 渠道");
        }
        throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "微信支付 SDK 尚未接入（阶段三扩展项）");
    }

    @Override
    public CallbackResult parseCallback(String rawBody, Map<String, String> headers) {
        throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "微信支付回调验签尚未实现（阶段三扩展项）");
    }

    @Override
    public RefundResult refund(PaymentOrder order, BigDecimal amount, String refundNo) {
        throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "微信退款尚未实现（阶段三扩展项）");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
