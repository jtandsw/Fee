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
 * 适用范围兼容工具：历史数据是「单一维度 + JSON 数组」，新数据是 {@link ScopeRule} 的组合条件，
 * 匹配逻辑统一收敛到 {@link ScopeRule#matches}。
 */
public final class ScopeUtils {

    private ScopeUtils() {
    }

    /** 解析历史格式的 JSON 数组（COMPOSITE 的对象格式会返回空列表） */
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
     * 判断学生是否命中项目适用范围（年级/学院/专业/指定学生，非空维度取交集）。
     */
    public static boolean matches(ObjectMapper objectMapper, PaymentItem item, Student student) {
        if (item == null) {
            return false;
        }
        return ScopeRule.of(item.getTargetScope(), item.getScopeValue(), objectMapper).matches(student);
    }
}
