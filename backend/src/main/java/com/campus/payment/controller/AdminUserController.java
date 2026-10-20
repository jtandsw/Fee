package com.campus.payment.controller;

import com.campus.payment.common.LogOperation;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.dto.LogQueryDTO;
import com.campus.payment.dto.ResetPasswordDTO;
import com.campus.payment.dto.StatusDTO;
import com.campus.payment.dto.UserQueryDTO;
import com.campus.payment.dto.UserSaveDTO;
import com.campus.payment.service.OperationLogService;
import com.campus.payment.service.UserService;
import com.campus.payment.vo.ImportResultVO;
import com.campus.payment.vo.OperationLogVO;
import com.campus.payment.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final OperationLogService operationLogService;

    @Operation(summary = "用户分页（支持角色/关键字）")
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<UserVO>> page(@Valid @ParameterObject UserQueryDTO query) {
        return Result.ok(userService.page(query));
    }

    @Operation(summary = "新增用户")
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @LogOperation(module = "用户", action = "新增")
    public Result<UserVO> create(@Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(userService.create(dto));
    }

    @Operation(summary = "编辑用户")
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @LogOperation(module = "用户", action = "编辑")
    public Result<UserVO> update(@PathVariable Long id, @Valid @RequestBody UserSaveDTO dto) {
        return Result.ok(userService.update(id, dto));
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @LogOperation(module = "用户", action = "启禁用")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        userService.updateStatus(id, dto.getStatus());
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @LogOperation(module = "用户", action = "重置密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordDTO dto) {
        userService.resetPassword(id, dto.getNewPassword());
        return Result.ok();
    }

    @Operation(summary = "学生名单 CSV 导入")
    @PostMapping("/students/import")
    @PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
    @LogOperation(module = "学生", action = "名单导入")
    public Result<ImportResultVO> importStudents(@RequestPart("file") MultipartFile file) {
        return Result.ok(userService.importStudents(file));
    }

    @Operation(summary = "操作日志分页")
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<PageResult<OperationLogVO>> logs(@Valid @ParameterObject LogQueryDTO query) {
        return Result.ok(operationLogService.page(query));
    }
}
