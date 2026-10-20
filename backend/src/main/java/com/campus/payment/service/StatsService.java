package com.campus.payment.service;

import com.campus.payment.vo.CollegeStatVO;
import com.campus.payment.vo.ItemStatVO;
import com.campus.payment.vo.OverviewStatVO;
import com.campus.payment.vo.TrendPointVO;
import jakarta.servlet.http.HttpServletResponse;

import java.time.LocalDate;
import java.util.List;

public interface StatsService {

    OverviewStatVO overview(Long itemId);

    List<ItemStatVO> byItem(String term);

    List<CollegeStatVO> byCollege();

    List<TrendPointVO> trend(LocalDate start, LocalDate end, String granularity);

    void export(HttpServletResponse response);
}
