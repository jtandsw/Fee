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

    /**
     * 发起支付。
     *
     * @param channel 学生在收银台选择的支付渠道，为空时沿用下单时记录的渠道
     */
    PayVO pay(String orderNo, String channel);

    void close(String orderNo);

    /**
     * 收银台可选支付渠道（含未接入渠道，便于前端展示禁用态）。
     */
    java.util.List<com.campus.payment.vo.ChannelVO> channels();

    RefundVO refund(String orderNo, RefundCreateDTO dto);

    String voucherHtml(String orderNo);

    void handleCallback(String channel, String rawBody, Map<String, String> headers);
}
