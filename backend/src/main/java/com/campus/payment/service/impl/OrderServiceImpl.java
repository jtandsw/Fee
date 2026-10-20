package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.ItemStatus;
import com.campus.payment.common.constant.OrderStatus;
import com.campus.payment.config.AppProperties;
import com.campus.payment.config.PaymentProperties;
import com.campus.payment.dto.OrderCreateDTO;
import com.campus.payment.dto.OrderQueryDTO;
import com.campus.payment.dto.RefundCreateDTO;
import com.campus.payment.entity.PaymentItem;
import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.entity.RefundRecord;
import com.campus.payment.entity.Student;
import com.campus.payment.mapper.PaymentItemMapper;
import com.campus.payment.mapper.PaymentOrderMapper;
import com.campus.payment.mapper.RefundRecordMapper;
import com.campus.payment.mapper.StudentMapper;
import com.campus.payment.payment.PaymentChannelAdapter;
import com.campus.payment.payment.PaymentChannelRegistry;
import com.campus.payment.security.LoginUser;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.service.OrderService;
import com.campus.payment.util.NoGenerator;
import com.campus.payment.util.ScopeUtils;
import com.campus.payment.vo.OrderCreateVO;
import com.campus.payment.vo.OrderVO;
import com.campus.payment.vo.PayVO;
import com.campus.payment.vo.RefundVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final PaymentOrderMapper orderMapper;
    private final PaymentItemMapper itemMapper;
    private final StudentMapper studentMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final PaymentChannelRegistry channelRegistry;
    private final PaymentProperties paymentProperties;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCreateVO create(OrderCreateDTO dto, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BusinessException(ResultCode.IDEMPOTENCY_KEY_MISSING);
        }
        LoginUser loginUser = SecurityUtils.getCurrentUser();
        if (!loginUser.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN_ROLE);
        }
        Student student = requireStudent(loginUser);

        PaymentOrder existing = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getIdempotencyKey, idempotencyKey));
        if (existing != null) {
            if (!existing.getStudentId().equals(student.getId())) {
                throw new BusinessException(ResultCode.FORBIDDEN_DATA);
            }
            return toCreateVO(existing);
        }

        PaymentItem item = itemMapper.selectById(dto.getItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.ITEM_NOT_FOUND);
        }
        if (!ItemStatus.PUBLISHED.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "该项目未发布或已下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(item.getStartTime()) || now.isAfter(item.getEndTime())) {
            throw new BusinessException(ResultCode.ITEM_NOT_IN_WINDOW);
        }
        if (!ScopeUtils.matches(objectMapper, item, student)) {
            throw new BusinessException(ResultCode.FORBIDDEN_DATA, "该项目不在你的适用范围内");
        }

        BigDecimal paid = orderMapper.sumPaidAmount(student.getId(), item.getId());
        BigDecimal remaining = item.getAmount().subtract(paid);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.ALREADY_PAID);
        }

        // 已有未过期的待支付订单则直接复用，避免同一项目重复挂单
        PaymentOrder pending = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getStudentId, student.getId())
                .eq(PaymentOrder::getItemId, item.getId())
                .in(PaymentOrder::getStatus, OrderStatus.PENDING.name(), OrderStatus.PAYING.name())
                .gt(PaymentOrder::getExpireAt, now)
                .orderByDesc(PaymentOrder::getId)
                .last("LIMIT 1"));
        if (pending != null) {
            return toCreateVO(pending);
        }

        String channel = StringUtils.hasText(dto.getChannel()) ? dto.getChannel() : paymentProperties.getDefaultChannel();
        if (!channelRegistry.supports(channel)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "不支持的支付渠道：" + channel);
        }

        // 应收金额 = 剩余应缴。不能用 allowPartial 决定「全额 or 剩余」：
        // 部分退款后 allowPartial=0 的项目同样存在缺口，按全额收会造成超额收费。
        BigDecimal amount = remaining;

        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(NoGenerator.orderNo());
        order.setStudentId(student.getId());
        order.setItemId(item.getId());
        order.setAmount(amount);
        order.setPaidAmount(BigDecimal.ZERO);
        order.setChannel(channel.toUpperCase());
        order.setStatus(OrderStatus.PENDING.name());
        order.setIdempotencyKey(idempotencyKey);
        order.setExpireAt(now.plusMinutes(paymentProperties.getOrderExpireMinutes()));
        order.setRemark(dto.getRemark());

        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发下同一幂等键只允许成功一次，返回已存在订单
            PaymentOrder duplicated = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                    .eq(PaymentOrder::getIdempotencyKey, idempotencyKey));
            if (duplicated == null) {
                throw new BusinessException(ResultCode.DATA_CONFLICT, "订单创建冲突，请重试");
            }
            return toCreateVO(duplicated);
        }
        return toCreateVO(order);
    }

    @Override
    public PageResult<OrderVO> page(OrderQueryDTO query) {
        Long studentId = null;
        if (SecurityUtils.isStudent()) {
            studentId = SecurityUtils.getStudentId();
            if (studentId == null) {
                throw new BusinessException(ResultCode.FORBIDDEN_DATA);
            }
        }
        IPage<OrderVO> page = orderMapper.selectOrderPage(query.toPage(), studentId,
                query.getStatus(), query.getItemId(), query.getChannel(), query.getKeyword());
        return PageResult.of(page);
    }

    @Override
    public OrderVO detail(String orderNo) {
        OrderVO vo = orderMapper.selectOrderByNo(orderNo);
        if (vo == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        checkDataPermission(vo.getStudentId());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayVO pay(String orderNo) {
        PaymentOrder order = requireOrder(orderNo);
        LoginUser user = SecurityUtils.getCurrentUser();
        if (!user.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN_ROLE);
        }
        if (!order.getStudentId().equals(user.getStudentId())) {
            throw new BusinessException(ResultCode.FORBIDDEN_DATA);
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID);
        }
        if (order.getExpireAt() != null && order.getExpireAt().isBefore(LocalDateTime.now())) {
            int closed = orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                    .eq(PaymentOrder::getId, order.getId())
                    .eq(PaymentOrder::getStatus, OrderStatus.PENDING.name())
                    .set(PaymentOrder::getStatus, OrderStatus.CLOSED.name())
                    .set(PaymentOrder::getClosedAt, LocalDateTime.now()));
            if (closed > 0) {
                throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "订单已超时关闭，请重新下单");
            }
        }
        int updated = orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, OrderStatus.PENDING.name())
                .set(PaymentOrder::getStatus, OrderStatus.PAYING.name()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID);
        }
        order.setStatus(OrderStatus.PAYING.name());
        PaymentChannelAdapter adapter = channelRegistry.get(order.getChannel());
        return adapter.createPayment(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(String orderNo) {
        PaymentOrder order = requireOrder(orderNo);
        checkDataPermission(order.getStudentId());
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID,
                    OrderStatus.PAYING.name().equals(order.getStatus()) ? "支付处理中，暂不可关闭" : "仅待支付订单可关闭");
        }
        int updated = orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .eq(PaymentOrder::getId, order.getId())
                .eq(PaymentOrder::getStatus, OrderStatus.PENDING.name())
                .set(PaymentOrder::getStatus, OrderStatus.CLOSED.name())
                .set(PaymentOrder::getClosedAt, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundVO refund(String orderNo, RefundCreateDTO dto) {
        PaymentOrder order = requireOrder(orderNo);
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == null || !status.isPaid()) {
            throw new BusinessException(ResultCode.ORDER_STATUS_INVALID, "仅已支付订单可退款");
        }
        BigDecimal refunded = refundRecordMapper.sumSuccessAmount(orderNo);
        if (refunded == null) {
            refunded = BigDecimal.ZERO;
        }
        BigDecimal refundable = order.getPaidAmount().subtract(refunded);
        if (dto.getAmount().compareTo(refundable) > 0) {
            throw new BusinessException(ResultCode.REFUND_EXCEED,
                    "退款金额超出可退金额（可退 " + refundable.toPlainString() + " 元）");
        }

        String refundNo = NoGenerator.refundNo();
        PaymentChannelAdapter adapter = channelRegistry.get(order.getChannel());
        PaymentChannelAdapter.RefundResult result = adapter.refund(order, dto.getAmount(), refundNo);

        RefundRecord record = new RefundRecord();
        record.setRefundNo(refundNo);
        record.setOrderNo(orderNo);
        record.setAmount(dto.getAmount());
        record.setReason(dto.getReason());
        record.setOperatorId(SecurityUtils.getUserId());
        record.setStatus(result.success() ? "SUCCESS" : "FAILED");
        record.setRefundedAt(result.success() ? LocalDateTime.now() : null);
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        refundRecordMapper.insert(record);

        if (!result.success()) {
            throw new BusinessException(ResultCode.CHANNEL_NOT_SUPPORTED, "渠道退款失败：" + result.message());
        }

        BigDecimal totalRefunded = refunded.add(dto.getAmount());
        String newStatus = totalRefunded.compareTo(order.getPaidAmount()) >= 0
                ? OrderStatus.REFUNDED.name() : OrderStatus.PARTIAL_REFUND.name();
        orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                .eq(PaymentOrder::getId, order.getId())
                .set(PaymentOrder::getStatus, newStatus));

        RefundVO vo = new RefundVO();
        vo.setRefundNo(refundNo);
        vo.setOrderNo(orderNo);
        vo.setAmount(dto.getAmount());
        vo.setReason(dto.getReason());
        vo.setStatus("SUCCESS");
        vo.setRefundedAt(record.getRefundedAt());
        return vo;
    }

    @Override
    public String voucherHtml(String orderNo) {
        OrderVO order = detail(orderNo);
        String paidAt = order.getPaidAt() == null ? "-" : order.getPaidAt().toString().replace('T', ' ');
        return """
                <!DOCTYPE html>
                <html lang="zh-CN"><head><meta charset="UTF-8">
                <title>电子缴费凭证 %s</title>
                <style>
                  body{font-family:"Microsoft YaHei",sans-serif;background:#f5f7fa;padding:32px;color:#1f2d3d}
                  .card{max-width:720px;margin:0 auto;background:#fff;padding:40px;border-radius:8px;box-shadow:0 2px 12px rgba(0,0,0,.08)}
                  h1{font-size:22px;text-align:center;margin:0 0 8px}
                  .sub{text-align:center;color:#909399;font-size:13px;margin-bottom:28px}
                  table{width:100%%;border-collapse:collapse;font-size:14px}
                  td{padding:10px 8px;border-bottom:1px dashed #e4e7ed}
                  td.label{color:#909399;width:160px}
                  .footer{margin-top:28px;font-size:12px;color:#909399;text-align:center}
                </style></head>
                <body><div class="card">
                  <h1>电子缴费凭证</h1>
                  <div class="sub">仅作缴费证明，不作为财政票据</div>
                  <table>
                    <tr><td class="label">订单号</td><td>%s</td></tr>
                    <tr><td class="label">缴费项目</td><td>%s</td></tr>
                    <tr><td class="label">学号 / 姓名</td><td>%s / %s</td></tr>
                    <tr><td class="label">所属学院</td><td>%s</td></tr>
                    <tr><td class="label">缴费金额</td><td>￥%s</td></tr>
                    <tr><td class="label">支付渠道</td><td>%s</td></tr>
                    <tr><td class="label">渠道流水号</td><td>%s</td></tr>
                    <tr><td class="label">缴费时间</td><td>%s</td></tr>
                  </table>
                  <div class="footer">本凭证由大学生缴费管理系统生成，可在系统中核验订单状态。</div>
                </div></body></html>
                """.formatted(
                escape(orderNo), escape(orderNo), escape(order.getItemName()),
                escape(order.getStudentNo()), escape(order.getStudentName()), escape(order.getCollege()),
                order.getAmount().toPlainString(), escape(order.getChannel()),
                escape(order.getTransactionNo() == null ? "-" : order.getTransactionNo()),
                escape(paidAt));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String channel, String rawBody, Map<String, String> headers) {
        PaymentChannelAdapter adapter = channelRegistry.get(channel);
        PaymentChannelAdapter.CallbackResult result = adapter.parseCallback(rawBody, headers);
        PaymentOrder order = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getOrderNo, result.orderNo()));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        if (!adapter.channel().equalsIgnoreCase(order.getChannel())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "回调渠道与订单渠道不一致");
        }
        if (!result.success()) {
            if (OrderStatus.PAYING.name().equals(order.getStatus())) {
                orderMapper.update(null, Wrappers.<PaymentOrder>lambdaUpdate()
                        .eq(PaymentOrder::getId, order.getId())
                        .eq(PaymentOrder::getStatus, OrderStatus.PAYING.name())
                        .set(PaymentOrder::getStatus, OrderStatus.PENDING.name()));
            }
            log.info("payment failed callback orderNo={} message={}", order.getOrderNo(), result.message());
            return;
        }
        if (result.amount().compareTo(order.getAmount()) != 0) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "回调金额与订单金额不一致");
        }
        int rows;
        try {
            rows = orderMapper.markPaid(order.getOrderNo(), result.transactionNo(), result.amount());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.DUPLICATE_PAY, "渠道流水号已被使用");
        }
        if (rows == 0) {
            PaymentOrder latest = orderMapper.selectById(order.getId());
            OrderStatus status = latest == null ? null : OrderStatus.of(latest.getStatus());
            if (status != null && status.isPaid()) {
                // 幂等：重复回调直接视为成功
                return;
            }
            // 订单已关闭/已退款后仍收到支付成功回调：不做状态迁移，返回成功避免渠道无限重试，
            // 该笔款项会在后续对账中体现为差异（账单有、本地无有效订单），由财务人工核销。
            log.warn("payment success callback on non-payable order orderNo={} status={} transactionNo={}",
                    order.getOrderNo(), order.getStatus(), result.transactionNo());
            return;
        }
    }

    private PaymentOrder requireOrder(String orderNo) {
        PaymentOrder order = orderMapper.selectOne(Wrappers.<PaymentOrder>lambdaQuery()
                .eq(PaymentOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ResultCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void checkDataPermission(Long ownerStudentId) {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user.isStudent() && !ownerStudentId.equals(user.getStudentId())) {
            throw new BusinessException(ResultCode.FORBIDDEN_DATA);
        }
    }

    private Student requireStudent(LoginUser loginUser) {
        Student student = loginUser.getStudentId() == null ? null : studentMapper.selectById(loginUser.getStudentId());
        if (student == null) {
            student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery()
                    .eq(Student::getUserId, loginUser.getUserId()));
        }
        if (student == null) {
            throw new BusinessException(ResultCode.FORBIDDEN_DATA, "当前账号未绑定学籍信息");
        }
        return student;
    }

    private OrderCreateVO toCreateVO(PaymentOrder order) {
        OrderCreateVO vo = new OrderCreateVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setExpireAt(order.getExpireAt());
        vo.setPayUrl(appProperties.getFrontendBaseUrl() + "/student/cashier?orderNo=" + order.getOrderNo());
        return vo;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
