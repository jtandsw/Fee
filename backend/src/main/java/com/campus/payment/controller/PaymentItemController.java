package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.ItemQueryDTO;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.PaymentItemSaveDTO;
import com.campus.payment.dto.ScopeDTO;
import com.campus.payment.service.PaymentItemService;
import com.campus.payment.vo.ClosePreviewVO;
import com.campus.payment.vo.PaymentItemVO;
import com.campus.payment.vo.ScopeOptionsVO;
import com.campus.payment.vo.ScopePreviewVO;
import com.campus.payment.vo.StudentOptionVO;
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

import java.util.List;

@Tag(name = "缴费项目")
@RestController
@RequestMapping("/v1/payment-items")
@RequiredArgsConstructor
public class PaymentItemController {

    private final PaymentItemService paymentItemService;

    @Operation(summary = "分页查询项目（财务视角）")
    @GetMapping
    @PreAuthorize("hasRole('FINANCE')")
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

    @Operation(summary = "适用范围可选项（年级 / 学院 / 专业，取自学籍档案现存值）")
    @GetMapping("/scope-options")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<ScopeOptionsVO> scopeOptions() {
        return Result.ok(paymentItemService.scopeOptions());
    }

    @Operation(summary = "指定学生范围的候选学生（关键字搜索 / 按 ids 回显）")
    @GetMapping("/scope-students")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<List<StudentOptionVO>> scopeStudents(
            @Parameter(description = "学号或姓名，模糊匹配") @RequestParam(required = false) String keyword,
            @Parameter(description = "学生 id 列表，用于编辑时回显已选学生") @RequestParam(required = false) List<Long> ids) {
        return Result.ok(paymentItemService.scopeStudents(keyword, ids));
    }

    @Operation(summary = "适用范围覆盖人数预览（发布前确认能覆盖到学生）")
    @PostMapping("/scope-preview")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<ScopePreviewVO> previewScope(@RequestBody(required = false) ScopeDTO scope) {
        return Result.ok(paymentItemService.previewScope(scope));
    }

    @Operation(summary = "项目详情（学生 / 财务）")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','FINANCE')")
    public Result<PaymentItemVO> detail(@PathVariable Long id) {
        return Result.ok(paymentItemService.detail(id));
    }

    @Operation(summary = "新建项目（草稿）")
    @PostMapping
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "缴费项目", action = "新建")
    public Result<PaymentItemVO> create(@Valid @RequestBody PaymentItemSaveDTO dto) {
        return Result.ok(paymentItemService.create(dto));
    }

    @Operation(summary = "修改项目（仅草稿）")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "缴费项目", action = "修改")
    public Result<PaymentItemVO> update(@PathVariable Long id, @Valid @RequestBody PaymentItemSaveDTO dto) {
        return Result.ok(paymentItemService.update(id, dto));
    }

    @Operation(summary = "发布项目")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "缴费项目", action = "发布")
    public Result<Void> publish(@PathVariable Long id) {
        paymentItemService.publish(id);
        return Result.ok();
    }

    @Operation(summary = "下架前影响预览（已缴人数与金额、未完成订单）")
    @GetMapping("/{id}/close-preview")
    @PreAuthorize("hasRole('FINANCE')")
    public Result<ClosePreviewVO> closePreview(@PathVariable Long id) {
        return Result.ok(paymentItemService.closePreview(id));
    }

    @Operation(summary = "下架项目（不退款；存在未完成订单时拒绝）")
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('FINANCE')")
    @LogOperation(module = "缴费项目", action = "下架")
    public Result<Void> close(@PathVariable Long id) {
        paymentItemService.close(id);
        return Result.ok();
    }
}
