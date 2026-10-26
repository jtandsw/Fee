package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 学籍档案中现存的「学院-专业」组合，用于发布项目时选择适用范围 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "学院-专业选项")
public class MajorOptionVO {

    @Schema(description = "学院")
    private String college;

    @Schema(description = "专业")
    private String major;
}
