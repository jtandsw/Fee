package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "退款结果")
public class RefundVO {
    private String refundNo;
    private String orderNo;
    private BigDecimal amount;
    private String reason;
    private String status;
    private LocalDateTime refundedAt;
}
