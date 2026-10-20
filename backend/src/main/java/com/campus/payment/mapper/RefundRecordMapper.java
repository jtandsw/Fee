package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.payment.entity.RefundRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface RefundRecordMapper extends BaseMapper<RefundRecord> {

    /** 已成功退款总额，用于校验「退款金额不得超出实缴」 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM refund_record WHERE order_no = #{orderNo} AND status = 'SUCCESS'")
    BigDecimal sumSuccessAmount(@Param("orderNo") String orderNo);
}
