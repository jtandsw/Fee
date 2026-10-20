package com.campus.payment.controller;

import com.campus.payment.common.Result;
import com.campus.payment.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "支付回调")
@RestController
@RequestMapping("/v1/pay")
@RequiredArgsConstructor
public class PayCallbackController {

    private final OrderService orderService;

    @Operation(summary = "渠道异步回调（匿名，验签由适配器完成）")
    @PostMapping("/callback/{channel}")
    public Result<Void> callback(@PathVariable String channel,
                                 @RequestBody String rawBody,
                                 @RequestHeader Map<String, String> headers) {
        orderService.handleCallback(channel, rawBody, headers);
        return Result.ok();
    }
}
