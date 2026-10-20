package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.ReconDetailHandleDTO;
import com.campus.payment.vo.ReconBatchVO;
import com.campus.payment.vo.ReconDetailVO;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

public interface ReconciliationService {

    PageResult<ReconBatchVO> page(PageQuery query);

    ReconBatchVO create(String channel, LocalDate billDate, MultipartFile file);

    ReconBatchVO match(String batchNo);

    PageResult<ReconDetailVO> details(String batchNo, String diffType, PageQuery query);

    void handle(Long detailId, ReconDetailHandleDTO dto);
}
