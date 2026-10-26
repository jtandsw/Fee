package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.NoticeSaveDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.service.NoticeService;
import com.campus.payment.vo.NoticeVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知提醒")
@RestController
@RequestMapping("/v1/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "通知列表（按角色过滤）")
    @GetMapping
    public Result<PageResult<NoticeVO>> page(@Valid @ParameterObject PageQuery query) {
        return Result.ok(noticeService.page(query));
    }

    @Operation(summary = "未读数")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(noticeService.unreadCount());
    }

    @Operation(summary = "通知详情（自动标记已读）")
    @GetMapping("/{id}")
    public Result<NoticeVO> detail(@PathVariable Long id) {
        return Result.ok(noticeService.detail(id));
    }

    @Operation(summary = "创建通知")
    @PostMapping
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "通知", action = "创建")
    public Result<NoticeVO> create(@Valid @RequestBody NoticeSaveDTO dto) {
        return Result.ok(noticeService.create(dto));
    }

    @Operation(summary = "发布通知")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "通知", action = "发布")
    public Result<Void> publish(@PathVariable Long id) {
        noticeService.publish(id);
        return Result.ok();
    }
}
