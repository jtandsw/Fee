package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "缴费项目")
public class PaymentItemVO {
    private Long id;
    private String itemNo;
    private String itemName;
    private String category;
    private String term;
    private BigDecimal amount;
    private Integer allowPartial;
    private String targetScope;
    private List<String> scopeValue;

    @Schema(description = "适用范围条件（统一格式，兼容历史数据）")
    private ScopeVO scope;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private String description;
    private Long createdBy;
    private LocalDateTime createdAt;

    // ---------- 仅学生视角（/available）会填充的缴费进度字段 ----------

    @Schema(description = "当前学生在该项目上已净缴金额（已支付 - 成功退款）")
    private BigDecimal paidAmount;

    @Schema(description = "当前学生剩余应缴金额")
    private BigDecimal remainAmount;

    @Schema(description = "当前学生的缴费状态：UNPAID / PARTIAL / PAID")
    private String payStatus;
}
