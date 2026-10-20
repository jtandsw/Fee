package com.campus.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserSaveDTO {

    @NotBlank(message = "登录账号不能为空")
    @Size(max = 50, message = "登录账号最长 50 字")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名最长 50 字")
    private String realName;

    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "STUDENT|FINANCE|ADMIN", message = "角色只能为 STUDENT/FINANCE/ADMIN")
    private String role;

    @Size(max = 20, message = "手机号最长 20 位")
    private String phone;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    // 学生角色必填
    private String studentNo;
    private String college;
    private String major;
    private String className;
    private Integer grade;
}
