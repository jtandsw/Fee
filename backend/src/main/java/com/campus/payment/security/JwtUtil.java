package com.campus.payment.security;

import com.campus.payment.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与解析（jjwt 0.12.5）。
 * access token 携带 uid/sid/role，refresh token 仅携带 uid 与 jti。
 */
@Component
public class JwtUtil {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtUtil(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(LoginUser user, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getUserId()))
                .claim("typ", TYPE_ACCESS)
                .claim("uid", user.getUserId())
                .claim("sid", user.getStudentId())
                .claim("username", user.getUsername())
                .claim("realName", user.getRealName())
                .claim("role", user.getRole())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.getAccessExpireSeconds() * 1000))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(LoginUser user, String jti) {
        Date now = new Date();
        return Jwts.builder()
                .id(jti)
                .subject(String.valueOf(user.getUserId()))
                .claim("typ", TYPE_REFRESH)
                .claim("uid", user.getUserId())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.getRefreshExpireSeconds() * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * @throws io.jsonwebtoken.ExpiredJwtException 令牌过期
     * @throws io.jsonwebtoken.JwtException        签名或格式非法
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public long getAccessExpireSeconds() {
        return properties.getAccessExpireSeconds();
    }

    public long getRefreshExpireSeconds() {
        return properties.getRefreshExpireSeconds();
    }
}
