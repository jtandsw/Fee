package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.ItemStatus;
import com.campus.payment.common.constant.PaymentCategory;
import com.campus.payment.common.constant.TargetScope;
import com.campus.payment.dto.ItemPaidDTO;
import com.campus.payment.dto.ItemQueryDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.PaymentItemSaveDTO;
import com.campus.payment.entity.PaymentItem;
import com.campus.payment.entity.Student;
import com.campus.payment.mapper.PaymentItemMapper;
import com.campus.payment.mapper.PaymentOrderMapper;
import com.campus.payment.mapper.StudentMapper;
import com.campus.payment.security.LoginUser;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.service.PaymentItemService;
import com.campus.payment.util.NoGenerator;
import com.campus.payment.util.ScopeUtils;
import com.campus.payment.vo.PaymentItemVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentItemServiceImpl implements PaymentItemService {

    /** 学生缴费进度（PaymentItemVO.payStatus），同时作为 /available 的 payFilter 取值 */
    private static final String PAY_UNPAID = "UNPAID";
    private static final String PAY_PARTIAL = "PARTIAL";
    private static final String PAY_PAID = "PAID";

    private final PaymentItemMapper itemMapper;
    private final StudentMapper studentMapper;
    private final PaymentOrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<PaymentItemVO> page(ItemQueryDTO query) {
        var wrapper = Wrappers.<PaymentItem>lambdaQuery()
                .eq(StringUtils.hasText(query.getStatus()), PaymentItem::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getCategory()), PaymentItem::getCategory, query.getCategory())
                .eq(StringUtils.hasText(query.getTerm()), PaymentItem::getTerm, query.getTerm())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(PaymentItem::getItemName, query.getKeyword())
                        .or()
                        .like(PaymentItem::getItemNo, query.getKeyword()))
                .orderByDesc(PaymentItem::getCreatedAt)
                .orderByDesc(PaymentItem::getId);
        IPage<PaymentItem> page = itemMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page.convert(this::toVO));
    }

    @Override
    public PageResult<PaymentItemVO> available(PageQuery query, String payFilter) {
        Student student = currentStudent();
        List<PaymentItem> all = itemMapper.selectAvailableList(student.getId(),
                student.getCollege(), student.getMajor(), student.getGrade());

        // 一次聚合拿到该学生在各项目上的净已缴金额（已支付 - 成功退款），避免逐条查询
        Map<Long, BigDecimal> paidMap = orderMapper.selectNetPaidByItem(student.getId()).stream()
                .collect(Collectors.toMap(ItemPaidDTO::getItemId,
                        d -> d.getPaid() == null ? BigDecimal.ZERO : d.getPaid(),
                        (a, b) -> a));

        String filter = StringUtils.hasText(payFilter) ? payFilter.trim().toUpperCase() : "ALL";
        List<PaymentItemVO> filtered = new ArrayList<>(all.size());
        for (PaymentItem item : all) {
            PaymentItemVO vo = toVO(item);
            BigDecimal amount = item.getAmount() == null ? BigDecimal.ZERO : item.getAmount();
            BigDecimal paid = paidMap.getOrDefault(item.getId(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal remain = amount.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            String payStatus = paid.signum() <= 0 ? PAY_UNPAID
                    : paid.compareTo(amount) >= 0 ? PAY_PAID : PAY_PARTIAL;
            vo.setPaidAmount(paid);
            vo.setRemainAmount(remain);
            vo.setPayStatus(payStatus);

            if (PAY_UNPAID.equals(filter) && PAY_PAID.equals(payStatus)) {
                continue;
            }
            if (PAY_PAID.equals(filter) && !PAY_PAID.equals(payStatus)) {
                continue;
            }
            filtered.add(vo);
        }

        int pageNum = (int) Math.max(query.getPageNum(), 1);
        int pageSize = (int) Math.max(query.getPageSize(), 1);
        int from = Math.min((pageNum - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        return PageResult.of(filtered.subList(from, to), filtered.size(), pageNum, pageSize);
    }

    @Override
    public PaymentItemVO detail(Long id) {
        PaymentItem item = requireItem(id);
        LoginUser user = SecurityUtils.getCurrentUser();
        if (user.isStudent()) {
            Student student = currentStudent();
            if (!ScopeUtils.matches(objectMapper, item, student)) {
                throw new BusinessException(ResultCode.FORBIDDEN_DATA);
            }
        }
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentItemVO create(PaymentItemSaveDTO dto) {
        validate(dto);
        PaymentItem item = new PaymentItem();
        applyDto(item, dto);
        item.setStatus(ItemStatus.DRAFT.name());
        item.setCreatedBy(SecurityUtils.getUserId());
        insertWithRetry(item, PaymentCategory.of(dto.getCategory()));
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentItemVO update(Long id, PaymentItemSaveDTO dto) {
        PaymentItem item = requireItem(id);
        if (!ItemStatus.DRAFT.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "仅草稿状态的项目可编辑");
        }
        validate(dto);
        applyDto(item, dto);
        itemMapper.updateById(item);
        return toVO(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        PaymentItem item = requireItem(id);
        if (!ItemStatus.DRAFT.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "仅草稿状态的项目可发布");
        }
        if (item.getEndTime() == null || item.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费截止时间必须晚于当前时间");
        }
        item.setStatus(ItemStatus.PUBLISHED.name());
        itemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        PaymentItem item = requireItem(id);
        if (!ItemStatus.PUBLISHED.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "仅已发布的项目可下架");
        }
        item.setStatus(ItemStatus.CLOSED.name());
        itemMapper.updateById(item);
    }

    private void validate(PaymentItemSaveDTO dto) {
        if (PaymentCategory.of(dto.getCategory()) == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费类别不正确");
        }
        TargetScope scope = TargetScope.of(dto.getTargetScope());
        if (scope == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "适用范围不正确");
        }
        if (scope.needScopeValue() && (dto.getScopeValue() == null || dto.getScopeValue().isEmpty())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "适用范围需填写具体值");
        }
        if (dto.getStartTime() == null || dto.getEndTime() == null
                || !dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费开始时间必须早于截止时间");
        }
        if (dto.getAllowPartial() == null || (dto.getAllowPartial() != 0 && dto.getAllowPartial() != 1)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "是否允许分次缴纳只能为 0 或 1");
        }
    }

    private void applyDto(PaymentItem item, PaymentItemSaveDTO dto) {
        item.setItemName(dto.getItemName());
        item.setCategory(dto.getCategory());
        item.setTerm(dto.getTerm());
        item.setAmount(dto.getAmount());
        item.setAllowPartial(dto.getAllowPartial());
        item.setTargetScope(dto.getTargetScope());
        item.setScopeValue(TargetScope.ALL.name().equals(dto.getTargetScope())
                ? null : ScopeUtils.toJson(objectMapper, dto.getScopeValue()));
        item.setStartTime(dto.getStartTime());
        item.setEndTime(dto.getEndTime());
        item.setDescription(dto.getDescription());
    }

    private void insertWithRetry(PaymentItem item, PaymentCategory category) {
        for (int attempt = 0; attempt < 3; attempt++) {
            item.setItemNo(nextItemNo(category));
            try {
                itemMapper.insert(item);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    throw new BusinessException(ResultCode.DATA_CONFLICT, "项目编号生成冲突，请重试");
                }
            }
        }
    }

    private String nextItemNo(PaymentCategory category) {
        int year = LocalDate.now().getYear();
        String prefix = category.getPrefix() + year;
        String max = itemMapper.selectMaxItemNo(prefix);
        int sequence = 1;
        if (max != null && max.length() > prefix.length()) {
            try {
                sequence = Integer.parseInt(max.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
                sequence = 1;
            }
        }
        return NoGenerator.itemNo(category.getPrefix(), year, sequence);
    }

    private PaymentItem requireItem(Long id) {
        PaymentItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(ResultCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    private Student currentStudent() {
        LoginUser user = SecurityUtils.getCurrentUser();
        if (!user.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN_ROLE);
        }
        Student student = user.getStudentId() == null ? null : studentMapper.selectById(user.getStudentId());
        if (student == null) {
            student = studentMapper.selectOne(Wrappers.<Student>lambdaQuery().eq(Student::getUserId, user.getUserId()));
        }
        if (student == null) {
            throw new BusinessException(ResultCode.FORBIDDEN_DATA, "当前账号未绑定学籍信息");
        }
        return student;
    }

    private PaymentItemVO toVO(PaymentItem item) {
        PaymentItemVO vo = new PaymentItemVO();
        BeanUtils.copyProperties(item, vo);
        List<String> scope = ScopeUtils.parse(objectMapper, item.getScopeValue());
        vo.setScopeValue(scope.isEmpty() ? null : scope);
        return vo;
    }
}
