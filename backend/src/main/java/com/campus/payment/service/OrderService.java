package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.OrderCreateDTO;
import com.campus.payment.dto.OrderQueryDTO;
import com.campus.payment.dto.RefundCreateDTO;
import com.campus.payment.vo.OrderCreateVO;
import com.campus.payment.vo.OrderVO;
import com.campus.payment.vo.PayVO;
import com.campus.payment.vo.RefundVO;

import java.util.Map;

public interface OrderService {

    OrderCreateVO create(OrderCreateDTO dto, String idempotencyKey);

    PageResult<OrderVO> page(OrderQueryDTO query);

    OrderVO detail(String orderNo);

    PayVO pay(String orderNo);

    void close(String orderNo);

    RefundVO refund(String orderNo, RefundCreateDTO dto);

    String voucherHtml(String orderNo);

    void handleCallback(String channel, String rawBody, Map<String, String> headers);
}
