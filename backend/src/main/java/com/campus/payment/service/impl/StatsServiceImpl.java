package com.campus.payment.service.impl;

import com.alibaba.excel.EasyExcel;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.ResultCode;
import com.campus.payment.mapper.PaymentOrderMapper;
import com.campus.payment.service.StatsService;
import com.campus.payment.vo.CollegeStatVO;
import com.campus.payment.vo.ItemStatVO;
import com.campus.payment.vo.OverviewStatVO;
import com.campus.payment.vo.TrendPointVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final PaymentOrderMapper orderMapper;

    @Override
    public OverviewStatVO overview(Long itemId) {
        OverviewStatVO vo = orderMapper.selectOverview(itemId);
        if (vo == null) {
            vo = new OverviewStatVO();
        }
        if (vo.getReceivable() == null) {
            vo.setReceivable(BigDecimal.ZERO);
        }
        if (vo.getReceived() == null) {
            vo.setReceived(BigDecimal.ZERO);
        }
        vo.setOutstanding(vo.getReceivable().subtract(vo.getReceived()).max(BigDecimal.ZERO));
        vo.setPayRate(vo.getReceivable().compareTo(BigDecimal.ZERO) > 0
                ? vo.getReceived().multiply(BigDecimal.valueOf(100)).divide(vo.getReceivable(), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        return vo;
    }

    @Override
    public List<ItemStatVO> byItem(String term) {
        return orderMapper.selectByItem(term);
    }

    @Override
    public List<CollegeStatVO> byCollege() {
        return orderMapper.selectByCollege();
    }

    @Override
    public List<TrendPointVO> trend(LocalDate start, LocalDate end, String granularity) {
        String unit = (granularity == null || granularity.isBlank()) ? "day" : granularity.toLowerCase();
        if (!"day".equals(unit) && !"month".equals(unit)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "granularity 只能为 day 或 month");
        }
        LocalDate today = LocalDate.now();
        LocalDate from = start == null ? today.minusDays(29) : start;
        LocalDate to = end == null ? today : end;
        if (from.isAfter(to)) {
            LocalDate temp = from;
            from = to;
            to = temp;
        }
        return orderMapper.selectTrend(from, to, unit);
    }

    @Override
    public void export(HttpServletResponse response) {
        List<ItemStatVO> rows = byItem(null);
        String fileName = URLEncoder.encode("缴费统计-" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName);
        try {
            EasyExcel.write(response.getOutputStream(), ItemStatVO.class)
                    .sheet("按项目统计")
                    .doWrite(rows);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "报表导出失败");
        }
    }
}
