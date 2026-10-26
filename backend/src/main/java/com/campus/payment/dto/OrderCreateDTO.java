package com.campus.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建订单：金额由服务端按项目配置与本人已缴情况校验，请求中的 amount 为本次缴纳金额")
public class OrderCreateDTO {

    @NotNull(message = "缴费项目不能为空")
    private Long itemId;

    @Schema(description = "支付渠道，默认取服务端 payment.default-channel")
    private String channel;

    /**
     * 本次缴纳金额。仅 allowPartial=1 的项目生效，留空表示缴清剩余应缴；
     * 不支持分次的项目传了不等于剩余应缴的金额会被拒绝。
     */
    @Schema(description = "本次缴纳金额，留空表示缴清剩余；仅支持分次缴纳的项目可小于剩余应缴")
    @DecimalMin(value = "0.01", message = "缴费金额不能小于 0.01 元")
    @Digits(integer = 8, fraction = 2, message = "缴费金额最多 8 位整数、2 位小数")
    private BigDecimal amount;

    private String remark;
}
