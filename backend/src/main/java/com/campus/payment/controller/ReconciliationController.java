package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.ReconDetailHandleDTO;
import com.campus.payment.service.ReconciliationService;
import com.campus.payment.vo.ReconBatchVO;
import com.campus.payment.vo.ReconDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Tag(name = "对账管理")
@RestController
@RequestMapping("/v1/reconciliations")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @Operation(summary = "对账批次分页")
    @GetMapping
    public Result<PageResult<ReconBatchVO>> page(@Valid @ParameterObject PageQuery query) {
        return Result.ok(reconciliationService.page(query));
    }

    @Operation(summary = "创建批次并上传账单 CSV")
    @PostMapping
    @LogOperation(module = "对账", action = "上传账单")
    public Result<ReconBatchVO> create(@RequestParam String channel,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate,
                                       @RequestPart("file") MultipartFile file) {
        return Result.ok(reconciliationService.create(channel, billDate, file));
    }

    @Operation(summary = "执行自动匹配")
    @PostMapping("/{batchNo}/match")
    @LogOperation(module = "对账", action = "自动匹配")
    public Result<ReconBatchVO> match(@PathVariable String batchNo) {
        return Result.ok(reconciliationService.match(batchNo));
    }

    @Operation(summary = "差异明细分页")
    @GetMapping("/{batchNo}/details")
    public Result<PageResult<ReconDetailVO>> details(@PathVariable String batchNo,
                                                     @RequestParam(required = false) String diffType,
                                                     @Valid @ParameterObject PageQuery query) {
        return Result.ok(reconciliationService.details(batchNo, diffType, query));
    }

    @Operation(summary = "人工核销")
    @PutMapping("/details/{id}/handle")
    @LogOperation(module = "对账", action = "人工核销")
    public Result<Void> handle(@PathVariable Long id, @Valid @RequestBody ReconDetailHandleDTO dto) {
        reconciliationService.handle(id, dto);
        return Result.ok();
    }
}
