package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.vo.CollegeStatVO;
import com.campus.payment.vo.ItemStatVO;
import com.campus.payment.vo.OrderVO;
import com.campus.payment.vo.OverviewStatVO;
import com.campus.payment.vo.TrendPointVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrder> {

    /**
     * 订单分页（连表取学生与项目名称，避免 N+1）。
     */
    IPage<OrderVO> selectOrderPage(IPage<OrderVO> page,
                                   @Param("studentId") Long studentId,
                                   @Param("status") String status,
                                   @Param("itemId") Long itemId,
                                   @Param("channel") String channel,
                                   @Param("studentNo") String studentNo,
                                   @Param("keyword") String keyword);

    /** 单条订单详情（连表） */
    OrderVO selectOrderByNo(@Param("orderNo") String orderNo);

    /** 某学生在某项目上的累计实缴 */
    /**
     * 某学生在单个项目上的净已缴金额 = 已支付 - 成功退款。
     * <p>
     * 必须扣掉 refund_record：退款不回写 paid_amount，否则部分退款后会被判定为「已缴清」而拒绝补缴。
     */
    @Select("SELECT COALESCE(SUM(o.paid_amount), 0) - COALESCE(SUM((" 
            + "    SELECT COALESCE(SUM(r.amount), 0) FROM refund_record r "
            + "    WHERE r.order_no = o.order_no AND r.status = 'SUCCESS'"
            + ")), 0) "
            + "FROM payment_order o "
            + "WHERE o.deleted = 0 AND o.student_id = #{studentId} AND o.item_id = #{itemId} "
            + "AND o.status IN ('PAID', 'PARTIAL_REFUND')")
    BigDecimal sumPaidAmount(@Param("studentId") Long studentId, @Param("itemId") Long itemId);

    /**
     * 按项目汇总某个学生的「净已缴金额」= 已支付金额 - 成功退款金额。
     * <p>
     * 退款不会回写 payment_order.paid_amount，因此余额必须在聚合时扣掉 refund_record，
     * 否则部分/全额退款后项目仍会被判定为「已缴清」。
     */
    @Select("SELECT o.item_id AS itemId, "
            + "       COALESCE(SUM(o.paid_amount), 0) - COALESCE(SUM(("
            + "           SELECT COALESCE(SUM(r.amount), 0) FROM refund_record r "
            + "           WHERE r.order_no = o.order_no AND r.status = 'SUCCESS'"
            + "       )), 0) AS paid "
            + "FROM payment_order o "
            + "WHERE o.deleted = 0 AND o.student_id = #{studentId} "
            + "  AND o.status IN ('PAID', 'PARTIAL_REFUND') "
            + "GROUP BY o.item_id "
            + "HAVING paid > 0")
    List<com.campus.payment.dto.ItemPaidDTO> selectNetPaidByItem(@Param("studentId") Long studentId);

    /**
     * 支付回调幂等落库：仅当订单处于 PAYING 时更新，影响行数为 0 视为已处理（README 3.2（2）/ Q12）。
     */
    int markPaid(@Param("orderNo") String orderNo,
                 @Param("transactionNo") String transactionNo,
                 @Param("paidAmount") BigDecimal paidAmount);

    /** 超时未支付订单批量关闭（OrderCloseJob） */
    int closeExpired();

    OverviewStatVO selectOverview(@Param("itemId") Long itemId);

    List<ItemStatVO> selectByItem(@Param("term") String term);

    List<CollegeStatVO> selectByCollege();

    List<TrendPointVO> selectTrend(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end,
                                   @Param("granularity") String granularity);

    /** 对账：按渠道与账单日期取本地已支付订单 */
    List<PaymentOrder> selectPaidByChannelAndDate(@Param("channel") String channel,
                                                  @Param("billDate") LocalDate billDate);

    /** 项目下架影响：已支付订单笔数（含部分退款） */
    @Select("SELECT COUNT(*) FROM payment_order "
            + "WHERE deleted = 0 AND item_id = #{itemId} AND status IN ('PAID', 'PARTIAL_REFUND')")
    int countPaidOrders(@Param("itemId") Long itemId);

    /** 项目下架影响：已缴费学生人数 */
    @Select("SELECT COUNT(DISTINCT student_id) FROM payment_order "
            + "WHERE deleted = 0 AND item_id = #{itemId} AND status IN ('PAID', 'PARTIAL_REFUND')")
    int countPaidStudents(@Param("itemId") Long itemId);

    /** 项目下架影响：已收金额合计（退款不回冲 paid_amount，此处按已支付口径） */
    @Select("SELECT COALESCE(SUM(paid_amount), 0) FROM payment_order "
            + "WHERE deleted = 0 AND item_id = #{itemId} AND status IN ('PAID', 'PARTIAL_REFUND')")
    BigDecimal sumPaidByItem(@Param("itemId") Long itemId);

    /** 项目下架影响：待支付与支付中的订单数，这类订单下架后仍可继续支付 */
    @Select("SELECT COUNT(*) FROM payment_order "
            + "WHERE deleted = 0 AND item_id = #{itemId} AND status IN ('PENDING', 'PAYING')")
    int countUnfinishedOrders(@Param("itemId") Long itemId);
}
