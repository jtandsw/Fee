package com.campus.payment.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生在某个缴费项目上的净已缴金额（已支付 - 成功退款）。
 */
@Data
public class ItemPaidDTO {
    private Long itemId;
    private BigDecimal paid;
}
