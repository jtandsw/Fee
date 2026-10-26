package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收银台支付渠道选项。
 * <p>
 * available 由后端判定，前端只负责渲染：未接入的渠道仍然展示，
 * 但标注为不可选，避免学生选了渠道却在支付时收到「渠道未接入」的错误。
 */
@Data
@AllArgsConstructor
@Schema(description = "支付渠道选项")
public class ChannelVO {

    @Schema(description = "渠道代码，与订单 channel 字段一致")
    private String value;

    @Schema(description = "渠道名称")
    private String label;

    @Schema(description = "当前环境是否可用于发起支付")
    private boolean available;

    @Schema(description = "不可用原因，可用时为空")
    private String remark;
}
