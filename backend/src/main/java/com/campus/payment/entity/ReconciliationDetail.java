package com.campus.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 对账明细表 reconciliation_detail */
@Data
@TableName("reconciliation_detail")
public class ReconciliationDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long batchId;

    private String orderNo;

    private String channelTradeNo;

    private BigDecimal localAmount;

    private BigDecimal channelAmount;

    private String diffType;

    private String handleStatus;

    private String handleRemark;

    private LocalDateTime createdAt;
}
