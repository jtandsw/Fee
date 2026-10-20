package com.campus.payment.job;

import com.campus.payment.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 截止前 3 天提醒（README 3.2（4））：每日 09:00 扫描即将截止的已发布项目并生成站内通知。
 * MVP 仅站内信；短信/邮件通道留待阶段二。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineNoticeJob {

    private final NoticeService noticeService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void pushDeadlineNotice() {
        try {
            int pushed = noticeService.pushDeadlineNotices();
            log.info("DeadlineNoticeJob pushed {} notices", pushed);
        } catch (Exception e) {
            log.error("DeadlineNoticeJob failed", e);
        }
    }
}
