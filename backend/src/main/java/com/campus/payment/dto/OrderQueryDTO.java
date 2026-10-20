package com.campus.payment.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderQueryDTO extends PageQuery {
    private String status;
    private Long itemId;
    private String channel;
    /** 订单号 / 渠道流水号 / 学号 / 姓名 */
    private String keyword;
}
