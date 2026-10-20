package com.campus.payment.controller;

import com.campus.payment.common.PageResult;
import com.campus.payment.common.Result;
import com.campus.payment.service.StatsService;
import com.campus.payment.vo.CollegeStatVO;
import com.campus.payment.vo.ItemStatVO;
import com.campus.payment.vo.OverviewStatVO;
import com.campus.payment.vo.TrendPointVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "统计报表")
@RestController
@RequestMapping("/v1/stats")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('FINANCE','ADMIN')")
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "总览：应收/实收/欠费率/笔数")
    @GetMapping("/overview")
    public Result<OverviewStatVO> overview(@RequestParam(required = false) Long itemId) {
        return Result.ok(statsService.overview(itemId));
    }

    @Operation(summary = "按项目统计")
    @GetMapping("/by-item")
    public Result<List<ItemStatVO>> byItem(@RequestParam(required = false) String term) {
        return Result.ok(statsService.byItem(term));
    }

    @Operation(summary = "按学院统计")
    @GetMapping("/by-college")
    public Result<List<CollegeStatVO>> byCollege() {
        return Result.ok(statsService.byCollege());
    }

    @Operation(summary = "按日/月趋势")
    @GetMapping("/trend")
    public Result<List<TrendPointVO>> trend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false, defaultValue = "day") String granularity) {
        return Result.ok(statsService.trend(start, end, granularity));
    }

    @Operation(summary = "导出 Excel（EasyExcel 流式）")
    @GetMapping("/export")
    public void export(HttpServletResponse response) {
        statsService.export(response);
    }
}
