package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.PageResult;
import com.campus.payment.dto.LogQueryDTO;
import com.campus.payment.entity.OperationLog;
import com.campus.payment.mapper.OperationLogMapper;
import com.campus.payment.service.OperationLogService;
import com.campus.payment.vo.OperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {

    private final OperationLogMapper operationLogMapper;

    @Override
    public PageResult<OperationLogVO> page(LogQueryDTO query) {
        IPage<OperationLog> page = operationLogMapper.selectPage(query.toPage(),
                Wrappers.<OperationLog>lambdaQuery()
                        .like(StringUtils.hasText(query.getUsername()), OperationLog::getUsername, query.getUsername())
                        .eq(StringUtils.hasText(query.getModule()), OperationLog::getModule, query.getModule())
                        .ge(query.getStartDate() != null, OperationLog::getCreatedAt,
                                query.getStartDate() == null ? null : query.getStartDate().atStartOfDay())
                        .lt(query.getEndDate() != null, OperationLog::getCreatedAt,
                                query.getEndDate() == null ? null : query.getEndDate().plusDays(1).atStartOfDay())
                        .orderByDesc(OperationLog::getId));
        return PageResult.of(page.convert(log -> {
            OperationLogVO vo = new OperationLogVO();
            BeanUtils.copyProperties(log, vo);
            return vo;
        }));
    }

    @Override
    public void record(String module, String action, String method, String uri, String ip,
                       String params, Integer resultCode, int costMs) {
        OperationLog log = new OperationLog();
        try {
            var user = com.campus.payment.security.SecurityUtils.getCurrentUser();
            log.setUserId(user.getUserId());
            log.setUsername(user.getUsername());
        } catch (Exception ignored) {
            // 匿名请求（如支付回调）不记录操作人
        }
        log.setModule(module);
        log.setAction(action);
        log.setMethod(method);
        log.setUri(uri);
        log.setIp(ip);
        log.setParams(params);
        log.setResultCode(resultCode);
        log.setCostMs(costMs);
        log.setCreatedAt(LocalDateTime.now());
        operationLogMapper.insert(log);
    }
}
