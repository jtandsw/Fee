package com.campus.payment.security;

import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录人（README 2.3 数据权限依赖此工具）。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long getUserId() {
        return getCurrentUser().getUserId();
    }

    public static Long getStudentId() {
        return getCurrentUser().getStudentId();
    }

    public static String getRole() {
        return getCurrentUser().getRole();
    }

    public static boolean isStudent() {
        return getCurrentUser().isStudent();
    }
}
