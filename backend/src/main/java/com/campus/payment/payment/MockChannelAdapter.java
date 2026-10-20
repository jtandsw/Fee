package com.campus.payment.payment;

import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import com.campus.payment.config.AppProperties;
import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.vo.PayVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟支付渠道（MVP 默认）：不依赖真实商户号，用于联调与演示。
 * 回调请求体：{"orderNo":"...","transactionNo":"...","amount":"5800.00","status":"SUCCESS"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockChannelAdapter implements PaymentChannelAdapter {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public PayVO createPayment(PaymentOrder order) {
        PayVO vo = new PayVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setChannel(channel());
        vo.setExpireAt(order.getExpireAt());
        vo.setPayUrl(appProperties.getFrontendBaseUrl() + "/student/cashier?orderNo=" + order.getOrderNo());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("orderNo", order.getOrderNo());
        params.put("amount", order.getAmount().toPlainString());
        params.put("mock", true);
        vo.setPayParams(params);
        return vo;
    }

    @Override
    public CallbackResult parseCallback(String rawBody, Map<String, String> headers) {
        try {
            MockCallback body = objectMapper.readValue(rawBody, MockCallback.class);
            if (body.orderNo() == null || body.transactionNo() == null || body.amount() == null) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "回调缺少 orderNo/transactionNo/amount");
            }
            return new CallbackResult(body.orderNo(), body.transactionNo(), body.amount(),
                    "SUCCESS".equalsIgnoreCase(body.status()), body.status());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "回调报文解析失败");
        }
    }

    @Override
    public RefundResult refund(PaymentOrder order, BigDecimal amount, String refundNo) {
        log.info("mock refund orderNo={} amount={} refundNo={}", order.getOrderNo(), amount, refundNo);
        return new RefundResult(true, "模拟退款成功");
    }

    public record MockCallback(String orderNo, String transactionNo, BigDecimal amount, String status) {
    }
}
