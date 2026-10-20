package com.campus.payment.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌状态存储（MVP 单机内存实现）。
 * <p>
 * README 4.1 将 Redis 列为可选，MVP 未引入 Redis，因此：
 * <ul>
 *   <li>refresh token 的 jti 存内存，实现服务端可撤销；</li>
 *   <li>登出后的 access token 进黑名单直至自然过期。</li>
 * </ul>
 * 多实例部署时必须替换为 Redis 实现，见 docs/架构说明.md「未实现/存疑」。
 */
@Component
public class TokenStore {

    /** jti -> 过期时间戳（毫秒） */
    private final Map<String, Long> refreshTokens = new ConcurrentHashMap<>();

    /** jti -> 过期时间戳（毫秒） */
    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    public void saveRefresh(String jti, long expireSeconds) {
        refreshTokens.put(jti, System.currentTimeMillis() + expireSeconds * 1000);
    }

    public boolean isRefreshActive(String jti) {
        Long expireAt = refreshTokens.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            refreshTokens.remove(jti);
            return false;
        }
        return true;
    }

    public void revokeRefresh(String jti) {
        if (jti != null) {
            refreshTokens.remove(jti);
        }
    }

    public void blacklist(String jti, long ttlSeconds) {
        if (jti != null) {
            blacklist.put(jti, System.currentTimeMillis() + Math.max(ttlSeconds, 1) * 1000);
        }
    }

    public boolean isBlacklisted(String jti) {
        Long expireAt = blacklist.get(jti);
        if (expireAt == null) {
            return false;
        }
        if (expireAt < System.currentTimeMillis()) {
            blacklist.remove(jti);
            return false;
        }
        return true;
    }

    @Scheduled(fixedDelay = 600_000L)
    public void purgeExpired() {
        long now = System.currentTimeMillis();
        refreshTokens.entrySet().removeIf(e -> e.getValue() < now);
        blacklist.entrySet().removeIf(e -> e.getValue() < now);
    }
}
