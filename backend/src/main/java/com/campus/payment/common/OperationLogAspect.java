package com.campus.payment.common;

import com.campus.payment.service.OperationLogService;
import com.campus.payment.util.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/**
 * 审计切面：记录模块、动作、方法、URI、IP、脱敏参数、结果码与耗时。
 * 不记录请求体，避免密码等敏感信息落库。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private static final Pattern SENSITIVE = Pattern.compile("(?i)(password|token|authorization|secret)=([^&]*)");

    private final OperationLogService operationLogService;

    @Around("@annotation(operation)")
    public Object around(ProceedingJoinPoint point, LogOperation operation) throws Throwable {
        long start = System.currentTimeMillis();
        int code = 0;
        try {
            Object result = point.proceed();
            if (result instanceof Result<?> body) {
                code = body.getCode();
            }
            return result;
        } catch (Throwable e) {
            code = e instanceof BusinessException business ? business.getCode() : 50000;
            throw e;
        } finally {
            try {
                ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    String params = mask(request.getQueryString());
                    operationLogService.record(operation.module(), operation.action(), request.getMethod(),
                            request.getRequestURI(), IpUtils.getIp(request), params, code,
                            (int) (System.currentTimeMillis() - start));
                }
            } catch (Exception e) {
                log.warn("write operation log failed: {}", e.getMessage());
            }
        }
    }

    private String mask(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String masked = SENSITIVE.matcher(value).replaceAll("$1=***");
        return masked.length() > 1000 ? masked.substring(0, 1000) : masked;
    }
}
