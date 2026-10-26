package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 指定学生范围（CUSTOM）用的精简学籍信息 */
@Data
@Schema(description = "学生选项（学籍精简信息）")
public class StudentOptionVO {

    @Schema(description = "学生 id，即 scope_value 中保存的值")
    private Long id;

    private String studentNo;

    private String realName;

    private String college;

    private String major;

    private Integer grade;
}
