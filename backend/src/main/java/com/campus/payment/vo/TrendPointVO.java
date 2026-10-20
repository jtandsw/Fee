package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "趋势点")
public class TrendPointVO {
    /** 日期或月份，如 2026-10-19 / 2026-10 */
    private String statDate;
    private BigDecimal amount;
    private Long count;
}
