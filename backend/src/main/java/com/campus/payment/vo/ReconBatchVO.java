package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "对账批次")
public class ReconBatchVO {
    private String batchNo;
    private String channel;
    private LocalDate billDate;
    private Integer totalCount;
    private BigDecimal totalAmount;
    private Integer matchedCount;
    private Integer diffCount;
    private BigDecimal diffAmount;
    private String status;
    private String filePath;
    private LocalDateTime createdAt;
}
