package com.campus.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "创建订单：金额以服务端项目配置为准，请求中的 amount 仅作展示")
public class OrderCreateDTO {

    @NotNull(message = "缴费项目不能为空")
    private Long itemId;

    @Schema(description = "支付渠道，默认 MOCK")
    private String channel = "MOCK";

    /** 仅前端展示用，服务端忽略（README 3.2（2）） */
    private BigDecimal amount;

    private String remark;
}
