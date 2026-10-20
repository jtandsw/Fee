package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.ItemQueryDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.PaymentItemSaveDTO;
import com.campus.payment.vo.PaymentItemVO;

public interface PaymentItemService {

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

    void close(Long id);
}
