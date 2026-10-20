package com.campus.payment;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 生成 BCrypt 密码哈希（README 7.4 备用工具）。
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        String raw = args.length > 0 ? args[0] : "123456";
        System.out.println(new BCryptPasswordEncoder().encode(raw));
    }
}
