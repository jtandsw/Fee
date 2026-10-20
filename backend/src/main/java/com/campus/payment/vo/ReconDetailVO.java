package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Schema(description = "对账明细")
public class ReconDetailVO {
    private Long id;
    private Long batchId;
    private String orderNo;
    private String channelTradeNo;
    private BigDecimal localAmount;
    private BigDecimal channelAmount;
    private String diffType;
    private String handleStatus;
    private String handleRemark;
    private LocalDateTime createdAt;
}
