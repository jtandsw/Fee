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
import com.campus.payment.dto.ScopeDTO;
import com.campus.payment.entity.PaymentItem;
import com.campus.payment.entity.Student;
import com.campus.payment.mapper.PaymentItemMapper;
import com.campus.payment.mapper.PaymentOrderMapper;
import com.campus.payment.mapper.StudentMapper;
import com.campus.payment.security.LoginUser;
import com.campus.payment.security.SecurityUtils;
import com.campus.payment.service.PaymentItemService;
import com.campus.payment.util.NoGenerator;
import com.campus.payment.util.ScopeRule;
import com.campus.payment.util.ScopeUtils;
import com.campus.payment.vo.ClosePreviewVO;
import com.campus.payment.vo.PaymentItemVO;
import com.campus.payment.vo.ScopeOptionsVO;
import com.campus.payment.vo.ScopePreviewVO;
import com.campus.payment.vo.ScopeVO;
import com.campus.payment.vo.StudentOptionVO;
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
import java.util.Objects;
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

    /** 指定学生范围时下拉的候选上限，以及关键字最大长度 */
    private static final int STUDENT_OPTION_LIMIT = 20;
    private static final int STUDENT_OPTION_MAX_IDS = 200;

    @Override
    public ScopeOptionsVO scopeOptions() {
        ScopeOptionsVO vo = new ScopeOptionsVO();
        vo.setGrades(studentMapper.selectScopeGrades());
        vo.setColleges(studentMapper.selectScopeColleges());
        vo.setMajors(studentMapper.selectScopeMajors());
        return vo;
    }

    @Override
    public List<StudentOptionVO> scopeStudents(String keyword, List<Long> ids) {
        List<Long> safeIds = ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().limit(STUDENT_OPTION_MAX_IDS).toList();
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if (kw != null && kw.length() > 50) {
            kw = kw.substring(0, 50);
        }
        // 有 ids 时按 ids 回显（回填已选学生）；否则按关键字搜索，未输入关键字则给前 N 条候选
        int limit = safeIds.isEmpty() ? STUDENT_OPTION_LIMIT : Math.max(safeIds.size(), 1);
        return studentMapper.selectScopeStudents(kw, safeIds, limit);
    }

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
        // 允许已下架项目恢复上架，避免误下架后不可挽回
        if (!ItemStatus.DRAFT.name().equals(item.getStatus())
                && !ItemStatus.CLOSED.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "仅草稿或已下架的项目可发布");
        }
        if (item.getEndTime() == null || item.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费截止时间必须晚于当前时间");
        }
        item.setStatus(ItemStatus.PUBLISHED.name());
        itemMapper.updateById(item);
    }

    @Override
    public ClosePreviewVO closePreview(Long id) {
        PaymentItem item = requireItem(id);
        int unfinished = orderMapper.countUnfinishedOrders(id);
        ClosePreviewVO vo = new ClosePreviewVO();
        vo.setItemId(item.getId());
        vo.setItemName(item.getItemName());
        vo.setPaidOrderCount(orderMapper.countPaidOrders(id));
        vo.setPaidStudentCount(orderMapper.countPaidStudents(id));
        vo.setPaidAmount(orderMapper.sumPaidByItem(id).toPlainString());
        vo.setUnfinishedOrderCount(unfinished);
        vo.setCanClose(ItemStatus.PUBLISHED.name().equals(item.getStatus()) && unfinished == 0);
        if (!ItemStatus.PUBLISHED.name().equals(item.getStatus())) {
            vo.setBlockReason("仅已发布的项目可下架");
        } else if (unfinished > 0) {
            vo.setBlockReason("还有 " + unfinished + " 笔待支付 / 支付中的订单，需先处理");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        PaymentItem item = requireItem(id);
        if (!ItemStatus.PUBLISHED.name().equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID, "仅已发布的项目可下架");
        }
        // 待支付 / 支付中的订单在下架后仍可继续支付，必须先处理，避免「项目已下架仍在收费」
        int unfinished = orderMapper.countUnfinishedOrders(id);
        if (unfinished > 0) {
            throw new BusinessException(ResultCode.ITEM_STATUS_INVALID,
                    "该项目还有 " + unfinished + " 笔待支付 / 支付中的订单，请先让学生完成支付或关闭订单后再下架");
        }
        item.setStatus(ItemStatus.CLOSED.name());
        itemMapper.updateById(item);
    }

    private void validate(PaymentItemSaveDTO dto) {
        if (PaymentCategory.of(dto.getCategory()) == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费类别不正确");
        }
        // 范围条件本身在 resolveScope 中校验并抛错，这里只做基础字段校验
        if (dto.getStartTime() == null || dto.getEndTime() == null
                || !dto.getStartTime().isBefore(dto.getEndTime())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缴费开始时间必须早于截止时间");
        }
        if (dto.getAllowPartial() == null || (dto.getAllowPartial() != 0 && dto.getAllowPartial() != 1)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "是否允许分次缴纳只能为 0 或 1");
        }
    }

    /**
     * 把入参翻译成统一的适用范围规则：
     * 优先用 {@code scope}（多维度组合），其次兼容老的 {@code targetScope + scopeValue}，都没传视为全校。
     */
    private ScopeRule resolveScope(PaymentItemSaveDTO dto) {
        ScopeDTO scope = dto.getScope();
        if (scope != null && (notEmpty(scope.getGrades()) || notEmpty(scope.getColleges())
                || notEmpty(scope.getMajors()) || notEmpty(scope.getStudentIds()))) {
            return ScopeRule.of(scope.getGrades(), scope.getColleges(), scope.getMajors(), scope.getStudentIds());
        }
        if (!StringUtils.hasText(dto.getTargetScope())) {
            return ScopeRule.empty();
        }
        TargetScope legacy = TargetScope.of(dto.getTargetScope());
        if (legacy == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "适用范围不正确");
        }
        if (!legacy.needScopeValue()) {
            return ScopeRule.empty();
        }
        if (!notEmpty(dto.getScopeValue())) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "适用范围需填写具体值");
        }
        return switch (legacy) {
            case GRADE -> ScopeRule.of(dto.getScopeValue(), null, null, null);
            case COLLEGE -> ScopeRule.of(null, dto.getScopeValue(), null, null);
            case MAJOR -> ScopeRule.of(null, null, dto.getScopeValue(), null);
            case CUSTOM -> ScopeRule.of(null, null, null, dto.getScopeValue());
            default -> ScopeRule.empty();
        };
    }

    private void applyDto(PaymentItem item, PaymentItemSaveDTO dto) {
        item.setItemName(dto.getItemName());
        item.setCategory(dto.getCategory());
        item.setTerm(dto.getTerm());
        item.setAmount(dto.getAmount());
        item.setAllowPartial(dto.getAllowPartial());
        ScopeRule rule = resolveScope(dto);
        // 条件全空 = 全校；否则统一写 COMPOSITE，四维度共存
        item.setTargetScope(rule.isEmpty() ? TargetScope.ALL.name() : TargetScope.COMPOSITE.name());
        item.setScopeValue(rule.isEmpty() ? null : rule.toJson(objectMapper));
        item.setStartTime(dto.getStartTime());
        item.setEndTime(dto.getEndTime());
        item.setDescription(dto.getDescription());
    }

    private static boolean notEmpty(List<String> values) {
        return values != null && !values.isEmpty();
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

        // 统一成四维度结构：历史单一维度数据也会被翻译成对应的一个维度，前端只有一套展示与编辑逻辑
        ScopeRule rule = ScopeRule.of(item.getTargetScope(), item.getScopeValue(), objectMapper);
        ScopeVO scopeVO = new ScopeVO();
        scopeVO.setGrades(rule.getGrades());
        scopeVO.setColleges(rule.getColleges());
        scopeVO.setMajors(rule.getMajors());
        scopeVO.setStudentIds(rule.getStudentIds());
        scopeVO.setText(rule.text());
        vo.setScope(scopeVO);
        return vo;
    }

    @Override
    public ScopePreviewVO previewScope(ScopeDTO scope) {
        ScopeRule rule = scope == null ? ScopeRule.empty()
                : ScopeRule.of(scope.getGrades(), scope.getColleges(), scope.getMajors(), scope.getStudentIds());
        ScopePreviewVO vo = new ScopePreviewVO();
        vo.setStudentCount(rule.isEmpty() ? studentMapper.countScopeStudents(null, null, null, null)
                : studentMapper.countScopeStudents(rule.getGrades(), rule.getColleges(),
                rule.getMajors(), rule.getStudentIds()));
        vo.setText(rule.text());
        return vo;
    }
}
