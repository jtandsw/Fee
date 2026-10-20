package com.campus.payment.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 业务单号生成：订单号 yyyyMMddHHmmss + 6 位随机数（README 3.2（2）），
 * 唯一性最终由数据库唯一索引兜底。
 */
public final class NoGenerator {

    private static final DateTimeFormatter SECOND = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private NoGenerator() {
    }

    public static String orderNo() {
        return LocalDateTime.now().format(SECOND) + randomDigits(6);
    }

    public static String refundNo() {
        return "RF" + LocalDateTime.now().format(SECOND) + randomDigits(4);
    }

    public static String batchNo(LocalDate billDate) {
        return "RC" + billDate.format(DAY) + randomDigits(4);
    }

    public static String itemNo(String prefix, int year, int sequence) {
        return prefix + year + String.format("%04d", sequence);
    }

    private static String randomDigits(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ThreadLocalRandom.current().nextInt(10));
        }
        return builder.toString();
    }
}
