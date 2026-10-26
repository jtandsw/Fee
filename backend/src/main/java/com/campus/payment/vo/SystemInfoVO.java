package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 系统运行信息（管理员视图）。
 * 只包含运行环境、账号规模、渠道接入状态与业务参数，
 * 刻意不包含应收/实收、订单、退款等财务业务数据。
 */
@Data
@Schema(description = "系统运行信息")
public class SystemInfoVO {

    @Schema(description = "应用运行环境")
    private App app;

    @Schema(description = "数据库连接信息")
    private Db db;

    @Schema(description = "账号规模统计")
    private Account account;

    @Schema(description = "支付渠道接入状态")
    private Payment payment;

    @Schema(description = "只读业务参数")
    private Setting setting;

    @Data
    public static class App {
        private String name;
        private String version;
        private String profile;
        private String contextPath;
        private String port;
        private String javaVersion;
        private String os;
        private String startedAt;
        private long uptimeSeconds;
    }

    @Data
    public static class Db {
        private String product;
        private String version;
        private String url;
        private String driver;
        private boolean connected;
        private String error;
    }

    @Data
    public static class Account {
        private long total;
        private long student;
        private long finance;
        private long admin;
        private long disabled;
    }

    @Data
    public static class Payment {
        private String defaultChannel;
        private long orderExpireMinutes;
        private List<Channel> channels;
    }

    @Data
    public static class Channel {
        private String value;
        private String label;
        /** 适配器是否已注册到渠道注册表 */
        private boolean registered;
        /** 是否能真正发起支付（当前阶段仅 MOCK 可用） */
        private boolean usable;
        private String remark;
    }

    @Data
    public static class Setting {
        private String frontendBaseUrl;
        private String uploadDir;
        private long accessTokenMinutes;
        private long refreshTokenDays;
        private List<String> corsOrigins;
    }
}
