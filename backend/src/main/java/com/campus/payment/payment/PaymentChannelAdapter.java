package com.campus.payment.payment;

import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.vo.PayVO;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付渠道适配器（README 3 系统定位）：
 * 业务层只依赖本接口，后续接入真实商户号不改订单/退款代码。
 */
public interface PaymentChannelAdapter {

    /** 渠道标识，需与 {@link com.campus.payment.common.constant.PayChannel} 枚举值一致 */
    String channel();

    /** 发起支付，返回收银台地址或渠道支付参数 */
    PayVO createPayment(PaymentOrder order);

    /** 解析并验签异步回调 */
    CallbackResult parseCallback(String rawBody, Map<String, String> headers);

    /** 发起退款 */
    RefundResult refund(PaymentOrder order, BigDecimal amount, String refundNo);

    record CallbackResult(String orderNo, String transactionNo, BigDecimal amount, boolean success, String message) {
    }

    record RefundResult(boolean success, String message) {
    }
}
