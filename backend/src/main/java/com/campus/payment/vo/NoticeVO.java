package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "通知")
public class NoticeVO {
    private Long id;
    private String title;
    private String content;
    private String noticeType;
    private String targetRole;
    private Long itemId;
    private String status;
    private LocalDateTime publishAt;
    private LocalDateTime expireAt;
    private LocalDateTime createdAt;
    private Boolean read;
}
