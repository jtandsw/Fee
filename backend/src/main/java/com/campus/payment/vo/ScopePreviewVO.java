package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 发布前预览：当前适用范围条件覆盖多少在校生 */
@Data
@Schema(description = "适用范围覆盖人数预览")
public class ScopePreviewVO {

    @Schema(description = "命中的在校生人数；0 表示发出去没有任何学生能看到")
    private Integer studentCount;

    @Schema(description = "条件描述，如「2023 级 · 计算机学院」")
    private String text;
}
