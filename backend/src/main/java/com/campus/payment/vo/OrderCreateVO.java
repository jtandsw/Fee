package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "下单结果")
public class OrderCreateVO {
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private LocalDateTime expireAt;
    private String payUrl;
}
