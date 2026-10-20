package com.campus.payment.common.constant;

/**
 * 适用范围（README 3.2（1））：值存 scope_value JSON 数组。
 */
public enum TargetScope {
    ALL,
    GRADE,
    COLLEGE,
    MAJOR,
    CUSTOM;

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
