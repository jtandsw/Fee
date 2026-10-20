package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.NoticeSaveDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.vo.NoticeVO;

public interface NoticeService {

    PageResult<NoticeVO> page(PageQuery query);

    NoticeVO detail(Long id);

    long unreadCount();

    NoticeVO create(NoticeSaveDTO dto);

    void publish(Long id);

    /** 截止前 3 天提醒，供 DeadlineNoticeJob 调用 */
    int pushDeadlineNotices();
}
