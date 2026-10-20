package com.campus.payment.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 敏感操作审计标记（README 12.2 第 6 条）：
 * 发布项目、退款、对账核销、用户禁用等必须留下 operation_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    /** 模块名，如「缴费项目」 */
    String module();

    /** 动作名，如「发布」 */
    String action();
}
