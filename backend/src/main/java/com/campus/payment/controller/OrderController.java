package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.OrderCreateDTO;
import com.campus.payment.dto.OrderQueryDTO;
import com.campus.payment.dto.RefundCreateDTO;
import com.campus.payment.service.OrderService;
import com.campus.payment.vo.OrderCreateVO;
import com.campus.payment.vo.OrderVO;
import com.campus.payment.vo.PayVO;
import com.campus.payment.vo.RefundVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@Tag(name = "订单与支付")
@RestController
@RequestMapping("/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "创建订单（需 Idempotency-Key 请求头）")
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    @LogOperation(module = "订单", action = "下单")
    public Result<OrderCreateVO> create(@Valid @RequestBody OrderCreateDTO dto,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return Result.ok(orderService.create(dto, idempotencyKey));
    }

    @Operation(summary = "订单分页（学生自动过滤本人）")
    @GetMapping
    public Result<PageResult<OrderVO>> page(@Valid @ParameterObject OrderQueryDTO query) {
        return Result.ok(orderService.page(query));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.detail(orderNo));
    }

    @Operation(summary = "发起支付，返回收银台参数")
    @PostMapping("/{orderNo}/pay")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<PayVO> pay(@PathVariable String orderNo) {
        return Result.ok(orderService.pay(orderNo));
    }

    @Operation(summary = "关闭未支付订单")
    @PostMapping("/{orderNo}/close")
    @LogOperation(module = "订单", action = "关闭")
    public Result<Void> close(@PathVariable String orderNo) {
        orderService.close(orderNo);
        return Result.ok();
    }

    @Operation(summary = "退款（财务/管理员）")
    @PostMapping("/{orderNo}/refund")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "订单", action = "退款")
    public Result<RefundVO> refund(@PathVariable String orderNo, @Valid @RequestBody RefundCreateDTO dto) {
        return Result.ok(orderService.refund(orderNo, dto));
    }

    @Operation(summary = "下载电子缴费凭证")
    @GetMapping("/{orderNo}/voucher")
    public ResponseEntity<String> voucher(@PathVariable String orderNo) {
        String html = orderService.voucherHtml(orderNo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("voucher-" + orderNo + ".html", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(html);
    }
}
