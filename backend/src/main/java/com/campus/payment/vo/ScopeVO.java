package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/** 适用范围条件（返回用）：四个维度之间的非空维度为交集，全空表示全校 */
@Data
@Schema(description = "适用范围条件")
public class ScopeVO {

    private List<String> grades;

    private List<String> colleges;

    private List<String> majors;

    @Schema(description = "指定学生 id 列表")
    private List<String> studentIds;

    @Schema(description = "一句话描述，如「2023 级 · 计算机学院 · 软件工程」，全空为「全校」")
    private String text;
}
