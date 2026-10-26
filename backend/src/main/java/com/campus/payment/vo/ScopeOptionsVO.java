package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 缴费项目「适用范围」可选项。
 * <p>
 * 数据全部来自学生学籍档案（student 表）的现存值，避免财务手填后与学生档案不一致，
 * 导致发布出去的项目谁都匹配不上（学生端看不到）。
 */
@Data
@Schema(description = "适用范围可选项（来源：学籍档案现存值）")
public class ScopeOptionsVO {

    @Schema(description = "可选入学年级，如 [\"2024\",\"2023\"]")
    private List<String> grades;

    @Schema(description = "可选学院")
    private List<String> colleges;

    @Schema(description = "可选专业（含所属学院）")
    private List<MajorOptionVO> majors;
}
