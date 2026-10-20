package com.campus.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Schema(description = "新建/编辑缴费项目")
public class PaymentItemSaveDTO {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称最长 100 字")
    private String itemName;

    @NotBlank(message = "缴费类别不能为空")
    private String category;

    @NotBlank(message = "学年学期不能为空")
    @Size(max = 20, message = "学年学期最长 20 字")
    private String term;

    @NotNull(message = "应缴金额不能为空")
    @DecimalMin(value = "0.01", message = "金额必须大于 0")
    @DecimalMax(value = "99999999.99", message = "金额超出上限")
    @Digits(integer = 8, fraction = 2, message = "金额最多 8 位整数、2 位小数")
    private BigDecimal amount;

    @NotNull(message = "是否允许分次缴纳不能为空")
    private Integer allowPartial;

    @NotBlank(message = "适用范围不能为空")
    private String targetScope;

    private List<String> scopeValue;

    @NotNull(message = "缴费开始时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @NotNull(message = "缴费截止时间不能为空")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Size(max = 500, message = "缴费说明最长 500 字")
    private String description;
}
