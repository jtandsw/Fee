package com.campus.payment.job;

import com.campus.payment.mapper.PaymentOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 未支付订单超时关闭（README 3.2（2））：每 5 分钟扫描一次，关闭 expire_at 已过的 PENDING 订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseJob {

    private final PaymentOrderMapper orderMapper;

    @Scheduled(initialDelay = 60_000L, fixedDelay = 300_000L)
    public void closeExpiredOrders() {
        try {
            int closed = orderMapper.closeExpired();
            if (closed > 0) {
                log.info("OrderCloseJob closed {} expired orders", closed);
            }
        } catch (Exception e) {
            log.error("OrderCloseJob failed", e);
        }
    }
}
