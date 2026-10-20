package com.campus.payment.util;

import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import com.campus.payment.entity.PaymentItem;
import com.campus.payment.entity.Student;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 适用范围（scope_value JSON 数组）解析与匹配，对应 README 3.2（1）。
 */
public final class ScopeUtils {

    private ScopeUtils() {
    }

    public static List<String> parse(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static String toJson(ObjectMapper objectMapper, List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "适用范围格式不正确");
        }
    }

    /**
     * 判断学生是否命中项目适用范围。
     */
    public static boolean matches(ObjectMapper objectMapper, PaymentItem item, Student student) {
        if (item == null || student == null) {
            return false;
        }
        String scope = item.getTargetScope();
        if ("ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        List<String> values = parse(objectMapper, item.getScopeValue());
        if (values.isEmpty()) {
            return false;
        }
        return switch (scope == null ? "" : scope.toUpperCase()) {
            case "GRADE" -> contains(values, String.valueOf(student.getGrade()));
            case "COLLEGE" -> contains(values, student.getCollege());
            case "MAJOR" -> contains(values, student.getMajor());
            case "CUSTOM" -> contains(values, String.valueOf(student.getId()));
            default -> false;
        };
    }

    private static boolean contains(List<String> values, String target) {
        return target != null && values.stream().anyMatch(v -> target.equalsIgnoreCase(v.trim()));
    }
}
