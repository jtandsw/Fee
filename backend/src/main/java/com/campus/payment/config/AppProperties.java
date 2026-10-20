package com.campus.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用级自定义配置（README 8.1 之外的补充项，全部可用环境变量覆盖）。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** 前端地址，用于拼接收银台 payUrl */
    private String frontendBaseUrl = "http://localhost:5173";

    /** 对账账单文件落盘目录 */
    private String uploadDir = "./uploads";

    private Cors cors = new Cors();

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));
    }
}
