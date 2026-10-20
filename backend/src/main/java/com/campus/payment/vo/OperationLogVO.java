package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "操作日志")
public class OperationLogVO {
    private Long id;
    private Long userId;
    private String username;
    private String module;
    private String action;
    private String method;
    private String uri;
    private String ip;
    private Integer resultCode;
    private Integer costMs;
    private LocalDateTime createdAt;
}
