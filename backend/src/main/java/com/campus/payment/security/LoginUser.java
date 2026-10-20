package com.campus.payment.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录态主体，放入 SecurityContext，token 中携带同样字段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    private Long userId;

    /** 学生身份对应的 student.id，非学生为 null */
    private Long studentId;

    private String username;

    private String realName;

    private String role;

    public boolean isStudent() {
        return "STUDENT".equals(role);
    }
}
