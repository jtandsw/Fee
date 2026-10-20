package com.campus.payment.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "按项目统计 / Excel 导出行")
public class ItemStatVO {
    @ExcelProperty("项目编号")
    private String itemNo;
    @ExcelProperty("项目名称")
    private String itemName;
    @ExcelProperty("缴费类别")
    private String category;
    @ExcelProperty("应缴单价(元)")
    private BigDecimal amount;
    @ExcelProperty("应收(元)")
    private BigDecimal receivable;
    @ExcelProperty("实收(元)")
    private BigDecimal received;
    @ExcelProperty("订单数")
    private Long orderCount;
    @ExcelProperty("已支付订单数")
    private Long paidCount;
}
