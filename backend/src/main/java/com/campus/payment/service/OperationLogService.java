package com.campus.payment.service;

import com.campus.payment.common.PageResult;
import com.campus.payment.dto.LogQueryDTO;
import com.campus.payment.vo.OperationLogVO;

public interface OperationLogService {

    PageResult<OperationLogVO> page(LogQueryDTO query);

    void record(String module, String action, String method, String uri, String ip,
                String params, Integer resultCode, int costMs);
}
