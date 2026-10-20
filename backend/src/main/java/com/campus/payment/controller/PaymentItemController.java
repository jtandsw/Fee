package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.ItemQueryDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.PaymentItemSaveDTO;
import com.campus.payment.service.PaymentItemService;
import com.campus.payment.vo.PaymentItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "缴费项目")
@RestController
@RequestMapping("/v1/payment-items")
@RequiredArgsConstructor
public class PaymentItemController {

    private final PaymentItemService paymentItemService;

    @Operation(summary = "分页查询项目（财务/管理员视角）")
    @GetMapping
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    public Result<PageResult<PaymentItemVO>> page(@Valid @ParameterObject ItemQueryDTO query) {
        return Result.ok(paymentItemService.page(query));
    }

    @Operation(summary = "当前学生可见项目（附本人缴费进度）")
    @GetMapping("/available")
    @PreAuthorize("hasRole('STUDENT')")
    public Result<PageResult<PaymentItemVO>> available(
            @Valid @ParameterObject PageQuery query,
            @Parameter(description = "缴费状态过滤：ALL(默认) / UNPAID 未缴清 / PAID 已缴清")
            @RequestParam(required = false, defaultValue = "ALL") String payFilter) {
        return Result.ok(paymentItemService.available(query, payFilter));
    }

    @Operation(summary = "项目详情")
    @GetMapping("/{id}")
    public Result<PaymentItemVO> detail(@PathVariable Long id) {
        return Result.ok(paymentItemService.detail(id));
    }

    @Operation(summary = "新建项目（草稿）")
    @PostMapping
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "缴费项目", action = "新建")
    public Result<PaymentItemVO> create(@Valid @RequestBody PaymentItemSaveDTO dto) {
        return Result.ok(paymentItemService.create(dto));
    }

    @Operation(summary = "修改项目（仅草稿）")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "缴费项目", action = "修改")
    public Result<PaymentItemVO> update(@PathVariable Long id, @Valid @RequestBody PaymentItemSaveDTO dto) {
        return Result.ok(paymentItemService.update(id, dto));
    }

    @Operation(summary = "发布项目")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "缴费项目", action = "发布")
    public Result<Void> publish(@PathVariable Long id) {
        paymentItemService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "下架项目")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "缴费项目", action = "下架")
    public Result<Void> close(@PathVariable Long id) {
        paymentItemService.close(id);
        return Result.ok();
    }
}
