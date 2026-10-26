package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 下架前的影响预览。
 * <p>
 * 下架只改变项目状态，不会退还已收费用，因此必须在操作前把影响面呈现给财务：
 * 有多少学生已缴费、收了多少钱、是否还有未完成的订单。
 */
@Data
@Schema(description = "项目下架影响预览")
public class ClosePreviewVO {

    @Schema(description = "项目 ID")
    private Long itemId;

    @Schema(description = "项目名称")
    private String itemName;

    @Schema(description = "已支付订单笔数（含部分退款）")
    private int paidOrderCount;

    @Schema(description = "已缴费学生人数")
    private int paidStudentCount;

    @Schema(description = "已收金额合计，退款不回冲")
    private String paidAmount;

    @Schema(description = "待支付与支付中的订单数，大于 0 时不允许下架")
    private int unfinishedOrderCount;

    @Schema(description = "当前是否允许下架")
    private boolean canClose;

    @Schema(description = "不可下架的原因")
    private String blockReason;
}
