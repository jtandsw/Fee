package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.constant.PayChannel;
import com.campus.payment.common.constant.Role;
import com.campus.payment.config.AppProperties;
import com.campus.payment.config.JwtProperties;
import com.campus.payment.config.PaymentProperties;
import com.campus.payment.entity.SysUser;
import com.campus.payment.mapper.SysUserMapper;
import com.campus.payment.payment.PaymentChannelRegistry;
import com.campus.payment.service.SystemInfoService;
import com.campus.payment.vo.SystemInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统信息实现：全部取自运行态真实数据（JVM、数据源元信息、sys_user 计数、渠道注册表、配置文件）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemInfoServiceImpl implements SystemInfoService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Environment environment;
    private final DataSource dataSource;
    private final SysUserMapper sysUserMapper;
    private final PaymentChannelRegistry channelRegistry;
    private final PaymentProperties paymentProperties;
    private final JwtProperties jwtProperties;
    private final AppProperties appProperties;

    @Override
    public SystemInfoVO info() {
        SystemInfoVO vo = new SystemInfoVO();
        vo.setApp(buildApp());
        vo.setDb(buildDb());
        vo.setAccount(buildAccount());
        vo.setPayment(buildPayment());
        vo.setSetting(buildSetting());
        return vo;
    }

    private SystemInfoVO.App buildApp() {
        SystemInfoVO.App app = new SystemInfoVO.App();
        app.setName(environment.getProperty("spring.application.name", "campus-payment"));
        String version = SystemInfoServiceImpl.class.getPackage().getImplementationVersion();
        app.setVersion(version == null ? "1.0.0" : version);
        String[] profiles = environment.getActiveProfiles();
        app.setProfile(profiles.length == 0 ? "default" : String.join(",", profiles));
        app.setContextPath(environment.getProperty("server.servlet.context-path", ""));
        app.setPort(environment.getProperty("server.port", "8080"));
        app.setJavaVersion(System.getProperty("java.version"));
        app.setOs(System.getProperty("os.name") + " " + System.getProperty("os.version"));
        long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
        app.setStartedAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(startedAt), ZoneId.systemDefault())
                .format(TIME_FORMAT));
        app.setUptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        return app;
    }

    private SystemInfoVO.Db buildDb() {
        SystemInfoVO.Db db = new SystemInfoVO.Db();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            db.setProduct(meta.getDatabaseProductName());
            db.setVersion(meta.getDatabaseProductVersion());
            db.setDriver(meta.getDriverName());
            db.setUrl(maskCredentials(meta.getURL()));
            db.setConnected(connection.isValid(2));
        } catch (Exception e) {
            // 数据库不可用时仍然返回其余信息，方便管理员判断是配置问题还是服务问题
            log.warn("read database meta failed", e);
            db.setConnected(false);
            db.setError(e.getMessage());
        }
        return db;
    }

    private SystemInfoVO.Account buildAccount() {
        SystemInfoVO.Account account = new SystemInfoVO.Account();
        account.setTotal(sysUserMapper.selectCount(null));
        account.setStudent(countByRole(Role.STUDENT));
        account.setFinance(countByRole(Role.FINANCE));
        account.setAdmin(countByRole(Role.ADMIN));
        account.setDisabled(sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, 0)));
        return account;
    }

    private long countByRole(Role role) {
        return sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRole, role.name()));
    }

    private SystemInfoVO.Payment buildPayment() {
        SystemInfoVO.Payment payment = new SystemInfoVO.Payment();
        payment.setDefaultChannel(paymentProperties.getDefaultChannel());
        payment.setOrderExpireMinutes(paymentProperties.getOrderExpireMinutes());
        List<SystemInfoVO.Channel> channels = new ArrayList<>();
        for (PayChannel channel : PayChannel.values()) {
            // 只列出已注册适配器的渠道，未实现的渠道不参与展示
            if (!channelRegistry.supports(channel.name())) {
                continue;
            }
            SystemInfoVO.Channel item = new SystemInfoVO.Channel();
            item.setValue(channel.name());
            item.setLabel(channelLabel(channel));
            item.setRegistered(true);
            item.setUsable(true);
            item.setRemark("演示渠道，不需要商户号，不产生真实扣款");
            channels.add(item);
        }
        payment.setChannels(channels);
        return payment;
    }

    private String channelLabel(PayChannel channel) {
        return switch (channel) {
            case MOCK -> "模拟支付";
        };
    }

    private SystemInfoVO.Setting buildSetting() {
        SystemInfoVO.Setting setting = new SystemInfoVO.Setting();
        setting.setFrontendBaseUrl(appProperties.getFrontendBaseUrl());
        setting.setUploadDir(appProperties.getUploadDir());
        setting.setAccessTokenMinutes(jwtProperties.getAccessExpireSeconds() / 60);
        setting.setRefreshTokenDays(jwtProperties.getRefreshExpireSeconds() / 86400);
        List<String> origins = appProperties.getCors() == null ? List.of() : appProperties.getCors().getAllowedOrigins();
        setting.setCorsOrigins(new ArrayList<>(origins == null ? List.of() : origins));
        return setting;
    }

    /** 抹掉 JDBC URL 里可能出现的账号口令，避免把凭据回显到页面上 */
    private String maskCredentials(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("(?i)(password|pwd|user)=[^&;]*", "$1=***");
    }
}
