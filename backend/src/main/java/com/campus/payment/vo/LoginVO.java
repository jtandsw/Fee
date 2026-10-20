package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录结果")
public class LoginVO {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private UserVO user;
}
