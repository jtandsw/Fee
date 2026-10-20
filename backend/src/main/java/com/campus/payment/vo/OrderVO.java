package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "订单（含学生与项目冗余展示字段）")
public class OrderVO {
    private String orderNo;
    private Long itemId;
    private String itemNo;
    private String itemName;
    private String category;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String college;
    private BigDecimal amount;
    private BigDecimal paidAmount;
    private String channel;
    private String status;
    private String transactionNo;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
}
