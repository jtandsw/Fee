package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.ItemQueryDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.PaymentItemSaveDTO;
import com.campus.payment.dto.ScopeDTO;
import com.campus.payment.vo.ClosePreviewVO;
import com.campus.payment.vo.PaymentItemVO;
import com.campus.payment.vo.ScopeOptionsVO;
import com.campus.payment.vo.ScopePreviewVO;
import com.campus.payment.vo.StudentOptionVO;

import java.util.List;

public interface PaymentItemService {

    /** 适用范围（年级 / 学院 / 专业）可选项，取自学籍档案现存值 */
    ScopeOptionsVO scopeOptions();

    /** 指定学生范围的候选学生：按关键字搜索，或按 ids 回显已选 */
    List<StudentOptionVO> scopeStudents(String keyword, List<Long> ids);

    /** 发布前预览：当前适用范围条件能覆盖多少名在校生（0 表示发出去没人能看到） */
    ScopePreviewVO previewScope(ScopeDTO scope);

    PageResult<PaymentItemVO> page(ItemQueryDTO query);

    /**
     * 学生可见项目，附带本人缴费进度。
     *
     * @param payFilter 缴费状态过滤：ALL / UNPAID（未缴清，含部分缴纳）/ PAID（已缴清）
     */
    PageResult<PaymentItemVO> available(PageQuery query, String payFilter);

    PaymentItemVO detail(Long id);

    PaymentItemVO create(PaymentItemSaveDTO dto);

    PaymentItemVO update(Long id, PaymentItemSaveDTO dto);

    void publish(Long id);

    /**
     * 下架前的影响预览：已缴人数与金额、未完成订单数。
     * 下架不会退款，财务需要据此判断是否可以下架。
     */
    ClosePreviewVO closePreview(Long id);

    void close(Long id);
}
