package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Schema(description = "发起支付返回的收银台参数")
public class PayVO {
    private String orderNo;
    private String channel;
    private String payUrl;
    private Map<String, Object> payParams;
    private LocalDateTime expireAt;
}
