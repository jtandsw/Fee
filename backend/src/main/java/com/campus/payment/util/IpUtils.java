package com.campus.payment.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取真实客户端 IP（兼容 Nginx 反代）。
 */
public final class IpUtils {

    private IpUtils() {
    }

    public static String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank() && !"unknown".equalsIgnoreCase(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
