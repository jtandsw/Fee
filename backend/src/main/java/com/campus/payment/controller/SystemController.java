package com.campus.payment.controller;

import com.campus.payment.common.Result;
import com.campus.payment.service.SystemInfoService;
import com.campus.payment.vo.SystemInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置：管理员专属，只暴露运行环境与参数，不提供任何财务业务查询。
 */
@Tag(name = "系统管理")
@RestController
@RequestMapping("/v1/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemController {

    private final SystemInfoService systemInfoService;

    @Operation(summary = "系统运行信息（仅管理员）")
    @GetMapping("/info")
    public Result<SystemInfoVO> info() {
        return Result.ok(systemInfoService.info());
    }
}
