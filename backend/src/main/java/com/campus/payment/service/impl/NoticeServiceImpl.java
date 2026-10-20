package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.ItemStatus;
import com.campus.payment.dto.NoticeSaveDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.entity.Notice;
import com.campus.payment.entity.NoticeRead;
import com.campus.payment.entity.PaymentItem;
import com.campus.payment.mapper.NoticeMapper;
import com.campus.payment.mapper.NoticeReadMapper;
import com.campus.payment.mapper.PaymentItemMapper;
import com.campus.payment.security.LoginUser;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.service.NoticeService;
import com.campus.payment.vo.NoticeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private static final Set<String> TARGET_ROLES = Set.of("STUDENT", "FINANCE", "ADMIN", "ALL");
    private static final Set<String> NOTICE_TYPES = Set.of("SYSTEM", "PAYMENT", "DEADLINE");

    private final NoticeMapper noticeMapper;
    private final NoticeReadMapper noticeReadMapper;
    private final PaymentItemMapper itemMapper;

    @Override
    public PageResult<NoticeVO> page(PageQuery query) {
        LoginUser user = SecurityUtils.getCurrentUser();
        // 财务/管理员需要看到自己创建的草稿才能发布，学生只看已发布的通知
        IPage<Notice> page = canManage(user.getRole())
                ? noticeMapper.selectPage(query.toPage(), Wrappers.<Notice>lambdaQuery()
                        .orderByDesc(Notice::getCreatedAt)
                        .orderByDesc(Notice::getId))
                : noticeMapper.selectVisiblePage(query.toPage(), user.getRole(), user.getUserId());
        List<Long> ids = page.getRecords().stream().map(Notice::getId).toList();
        Set<Long> readIds = ids.isEmpty() ? Set.of() : noticeReadMapper.selectList(
                        Wrappers.<NoticeRead>lambdaQuery()
                                .eq(NoticeRead::getUserId, user.getUserId())
                                .in(NoticeRead::getNoticeId, ids))
                .stream().map(NoticeRead::getNoticeId).collect(Collectors.toSet());
        return PageResult.of(page.convert(notice -> toVO(notice, readIds.contains(notice.getId()))));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoticeVO detail(Long id) {
        Notice notice = noticeMapper.selectById(id);
        LoginUser user = SecurityUtils.getCurrentUser();
        if (notice == null || (!canManage(user.getRole()) && !visible(notice, user.getRole()))) {
            throw new BusinessException(ResultCode.NOTICE_NOT_FOUND);
        }
        markRead(id, user.getUserId());
        return toVO(notice, true);
    }

    @Override
    public long unreadCount() {
        LoginUser user = SecurityUtils.getCurrentUser();
        return noticeMapper.countUnread(user.getRole(), user.getUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoticeVO create(NoticeSaveDTO dto) {
        if (!TARGET_ROLES.contains(dto.getTargetRole())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "targetRole 不正确");
        }
        if (!NOTICE_TYPES.contains(dto.getNoticeType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "noticeType 不正确");
        }
        Notice notice = new Notice();
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setNoticeType(dto.getNoticeType());
        notice.setTargetRole(dto.getTargetRole());
        notice.setItemId(dto.getItemId());
        notice.setStatus("DRAFT");
        notice.setExpireAt(dto.getExpireAt());
        notice.setCreatedBy(SecurityUtils.getUserId());
        noticeMapper.insert(notice);
        return toVO(notice, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        Notice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ResultCode.NOTICE_NOT_FOUND);
        }
        if (!"DRAFT".equals(notice.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅草稿状态的通知可发布");
        }
        notice.setStatus("PUBLISHED");
        notice.setPublishAt(LocalDateTime.now());
        noticeMapper.updateById(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pushDeadlineNotices() {
        LocalDateTime now = LocalDateTime.now();
        List<PaymentItem> items = itemMapper.selectList(Wrappers.<PaymentItem>lambdaQuery()
                .eq(PaymentItem::getStatus, ItemStatus.PUBLISHED.name())
                .ge(PaymentItem::getEndTime, now)
                .le(PaymentItem::getEndTime, now.plusDays(3)));
        int pushed = 0;
        for (PaymentItem item : items) {
            Long exists = noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                    .eq(Notice::getNoticeType, "DEADLINE")
                    .eq(Notice::getItemId, item.getId())
                    .ge(Notice::getCreatedAt, now.minusDays(1)));
            if (exists != null && exists > 0) {
                continue;
            }
            Notice notice = new Notice();
            notice.setTitle("缴费截止提醒：" + item.getItemName());
            notice.setContent("【" + item.getItemName() + "】将于 " + item.getEndTime()
                    + " 截止，应缴金额 " + item.getAmount().toPlainString()
                    + " 元，请尚未完成的同学尽快登录系统缴费。");
            notice.setNoticeType("DEADLINE");
            notice.setTargetRole("STUDENT");
            notice.setItemId(item.getId());
            notice.setStatus("PUBLISHED");
            notice.setPublishAt(now);
            notice.setCreatedBy(item.getCreatedBy());
            noticeMapper.insert(notice);
            pushed++;
        }
        return pushed;
    }

    private void markRead(Long noticeId, Long userId) {
        Long exists = noticeReadMapper.selectCount(Wrappers.<NoticeRead>lambdaQuery()
                .eq(NoticeRead::getNoticeId, noticeId)
                .eq(NoticeRead::getUserId, userId));
        if (exists != null && exists > 0) {
            return;
        }
        NoticeRead read = new NoticeRead();
        read.setNoticeId(noticeId);
        read.setUserId(userId);
        read.setReadAt(LocalDateTime.now());
        try {
            noticeReadMapper.insert(read);
        } catch (DuplicateKeyException ignored) {
            // 并发进入详情，唯一索引保证只写一条
        }
    }

    private boolean canManage(String role) {
        return "FINANCE".equals(role) || "ADMIN".equals(role);
    }

    private boolean visible(Notice notice, String role) {
        return "PUBLISHED".equals(notice.getStatus())
                && notice.getPublishAt() != null && !notice.getPublishAt().isAfter(LocalDateTime.now())
                && (notice.getExpireAt() == null || notice.getExpireAt().isAfter(LocalDateTime.now()))
                && ("ALL".equals(notice.getTargetRole()) || role.equals(notice.getTargetRole()));
    }

    private NoticeVO toVO(Notice notice, boolean read) {
        NoticeVO vo = new NoticeVO();
        vo.setId(notice.getId());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setNoticeType(notice.getNoticeType());
        vo.setTargetRole(notice.getTargetRole());
        vo.setItemId(notice.getItemId());
        vo.setStatus(notice.getStatus());
        vo.setPublishAt(notice.getPublishAt());
        vo.setExpireAt(notice.getExpireAt());
        vo.setCreatedAt(notice.getCreatedAt());
        vo.setRead(read);
        return vo;
    }
}
