package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "按学院统计")
public class CollegeStatVO {
    private String college;
    private Long studentCount;
    private BigDecimal receivable;
    private BigDecimal received;
}
