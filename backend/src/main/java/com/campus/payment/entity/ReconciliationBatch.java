package com.campus.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 对账批次表 reconciliation_batch */
@Data
@TableName("reconciliation_batch")
public class ReconciliationBatch {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String batchNo;

    private String channel;

    private LocalDate billDate;

    private Integer totalCount;

    private BigDecimal totalAmount;

    private Integer matchedCount;

    private Integer diffCount;

    private BigDecimal diffAmount;

    private String status;

    private String filePath;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
