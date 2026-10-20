package com.campus.payment.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 缴费项目表 payment_item */
@Data
@TableName("payment_item")
public class PaymentItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemNo;

    private String itemName;

    private String category;

    private String term;

    private BigDecimal amount;

    private Integer allowPartial;

    private String targetScope;

    private String scopeValue;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private String description;

    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
