package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.payment.entity.PaymentItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PaymentItemMapper extends BaseMapper<PaymentItem> {

    /**
     * 学生可见项目：已发布 + 在时间窗内 + 命中适用范围（README 3.2（1））。
     * 返回全量列表，缴费进度（是否已缴清）在 Service 层结合订单聚合做过滤与分页。
     */
    List<PaymentItem> selectAvailableList(@Param("studentId") Long studentId,
                                          @Param("college") String college,
                                          @Param("major") String major,
                                          @Param("grade") Integer grade);

    /** 取指定前缀下最大的项目编号（包含已逻辑删除的行，避免唯一索引冲突） */
    @Select("SELECT MAX(item_no) FROM payment_item WHERE item_no LIKE CONCAT(#{prefix}, '%')")
    String selectMaxItemNo(@Param("prefix") String prefix);
}
