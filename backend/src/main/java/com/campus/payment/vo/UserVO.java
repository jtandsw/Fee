package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户信息（不含密码）")
public class UserVO {
    private Long id;
    private String username;
    private String realName;
    private String role;
    private String phone;
    private String email;
    private Integer status;
    private String studentNo;
    private String college;
    private String major;
    private String className;
    private Integer grade;
}
