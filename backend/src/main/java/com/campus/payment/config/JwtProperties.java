package com.campus.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置（README 8.1 jwt 段）。
 */
@Data
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签名密钥，长度至少 32 字节 */
    private String secret;

    /** 访问令牌有效期（秒） */
    private long accessExpireSeconds = 7200;

    /** 刷新令牌有效期（秒） */
    private long refreshExpireSeconds = 604800;
}
