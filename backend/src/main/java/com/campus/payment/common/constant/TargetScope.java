package com.campus.payment.common.constant;

/**
 * 适用范围（README 3.2（1））。
 * <p>
 * 新数据统一写 {@link #COMPOSITE}：{@code scope_value} 存
 * {@code {"grades":[..],"colleges":[..],"majors":[..],"studentIds":[..]}}，非空维度取交集；
 * {@code ALL} 表示全校。GRADE / COLLEGE / MAJOR / CUSTOM 为历史数据格式，仅做兼容读取。
 */
public enum TargetScope {
    ALL,
    GRADE,
    COLLEGE,
    MAJOR,
    CUSTOM,
    COMPOSITE;

    public static TargetScope of(String value) {
        for (TargetScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value)) {
                return scope;
            }
        }
        return null;
    }

    public boolean needScopeValue() {
        return this != ALL;
    }
}
