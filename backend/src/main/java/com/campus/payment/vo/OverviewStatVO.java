package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "统计总览")
public class OverviewStatVO {
    /** 应收：非 CLOSED 订单的应缴金额之和 */
    private BigDecimal receivable = BigDecimal.ZERO;
    /** 实收：PAID / PARTIAL_REFUND 订单的实缴金额之和 */
    private BigDecimal received = BigDecimal.ZERO;
    /** 欠费：应收 - 实收 */
    private BigDecimal outstanding = BigDecimal.ZERO;
    /** 缴费完成率（实收/应收 * 100，两位小数） */
    private BigDecimal payRate = BigDecimal.ZERO;
    private Long orderCount = 0L;
    private Long paidCount = 0L;
    private Long studentCount = 0L;
}
