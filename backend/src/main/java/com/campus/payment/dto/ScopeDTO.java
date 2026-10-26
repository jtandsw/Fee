package com.campus.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 适用范围条件：四个维度可任意组合，非空维度之间取交集；全部留空表示全校。
 */
@Data
@Schema(description = "适用范围条件（维度之间为「并且」）")
public class ScopeDTO {

    @Size(max = 50, message = "年级最多选 50 个")
    @Schema(description = "入学年级，如 [\"2023\"]")
    private List<String> grades;

    @Size(max = 100, message = "学院最多选 100 个")
    private List<String> colleges;

    @Size(max = 200, message = "专业最多选 200 个")
    private List<String> majors;

    @Size(max = 500, message = "指定学生最多 500 人")
    @Schema(description = "学生 id 列表")
    private List<String> studentIds;
}
