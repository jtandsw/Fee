package com.campus.payment.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.payment.common.BusinessException;
import com.campus.payment.common.PageResult;
import com.campus.payment.common.ResultCode;
import com.campus.payment.common.constant.DiffType;
import com.campus.payment.common.constant.HandleStatus;
import com.campus.payment.common.constant.PayChannel;
import com.campus.payment.config.AppProperties;
import com.campus.payment.dto.PageQuery;
import com.campus.payment.dto.ReconDetailHandleDTO;
import com.campus.payment.entity.PaymentOrder;
import com.campus.payment.entity.ReconciliationBatch;
import com.campus.payment.entity.ReconciliationDetail;
import com.campus.payment.mapper.PaymentOrderMapper;
import com.campus.payment.mapper.ReconciliationBatchMapper;
import com.campus.payment.mapper.ReconciliationDetailMapper;
import com.campus.payment.service.ReconciliationService;
import com.campus.payment.util.NoGenerator;
import com.campus.payment.vo.ReconBatchVO;
import com.campus.payment.vo.ReconDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationBatchMapper batchMapper;
    private final ReconciliationDetailMapper detailMapper;
    private final PaymentOrderMapper orderMapper;
    private final AppProperties appProperties;

    @Override
    public PageResult<ReconBatchVO> page(PageQuery query) {
        IPage<ReconciliationBatch> page = batchMapper.selectPage(query.toPage(),
                Wrappers.<ReconciliationBatch>lambdaQuery()
                        .orderByDesc(ReconciliationBatch::getBillDate)
                        .orderByDesc(ReconciliationBatch::getId));
        return PageResult.of(page.convert(this::toBatchVO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconBatchVO create(String channel, LocalDate billDate, MultipartFile file) {
        if (billDate == null || !StringUtils.hasText(channel)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "渠道与账单日期不能为空");
        }
        if (PayChannel.of(channel) == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "对账渠道不正确");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "请上传账单 CSV 文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!original.endsWith(".csv")) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "账单文件必须为 CSV 格式");
        }
        Long exists = batchMapper.selectCount(Wrappers.<ReconciliationBatch>lambdaQuery()
                .eq(ReconciliationBatch::getChannel, channel.toUpperCase())
                .eq(ReconciliationBatch::getBillDate, billDate));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.BATCH_EXISTS);
        }

        String batchNo = NoGenerator.batchNo(billDate);
        // 使用绝对路径：Spring 的 Part.write() 对相对路径会落到容器临时目录，导致后续读取失败
        Path dir = Paths.get(appProperties.getUploadDir(), "recon").toAbsolutePath().normalize();
        Path target = dir.resolve(batchNo + ".csv");
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("save bill file failed", e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "账单文件保存失败");
        }

        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setBatchNo(batchNo);
        batch.setChannel(channel.toUpperCase());
        batch.setBillDate(billDate);
        batch.setTotalCount(0);
        batch.setTotalAmount(BigDecimal.ZERO);
        batch.setMatchedCount(0);
        batch.setDiffCount(0);
        batch.setDiffAmount(BigDecimal.ZERO);
        batch.setStatus("PENDING");
        batch.setFilePath(target.toAbsolutePath().toString());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.insert(batch);
        return toBatchVO(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReconBatchVO match(String batchNo) {
        ReconciliationBatch batch = requireBatch(batchNo);
        if ("FINISHED".equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.BATCH_STATUS_INVALID, "批次已归档，不允许重新匹配");
        }
        Path path = Paths.get(batch.getFilePath() == null ? "" : batch.getFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "账单文件不存在，请重新上传");
        }
        List<BillRow> rows = parseBill(path);
        List<PaymentOrder> locals = orderMapper.selectPaidByChannelAndDate(batch.getChannel(), batch.getBillDate());

        Map<String, PaymentOrder> byTxn = new HashMap<>();
        Map<String, PaymentOrder> byOrderNo = new HashMap<>();
        for (PaymentOrder order : locals) {
            if (StringUtils.hasText(order.getTransactionNo())) {
                byTxn.put(order.getTransactionNo(), order);
            }
            byOrderNo.put(order.getOrderNo(), order);
        }

        detailMapper.delete(Wrappers.<ReconciliationDetail>lambdaQuery()
                .eq(ReconciliationDetail::getBatchId, batch.getId()));

        Set<Long> usedLocalIds = new HashSet<>();
        int matched = 0;
        int diffCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal diffAmount = BigDecimal.ZERO;

        for (BillRow row : rows) {
            PaymentOrder local = StringUtils.hasText(row.channelTradeNo()) ? byTxn.get(row.channelTradeNo()) : null;
            if (local == null && StringUtils.hasText(row.orderNo())) {
                local = byOrderNo.get(row.orderNo());
            }
            totalAmount = totalAmount.add(row.amount());
            ReconciliationDetail detail = new ReconciliationDetail();
            detail.setBatchId(batch.getId());
            detail.setChannelTradeNo(row.channelTradeNo());
            detail.setChannelAmount(row.amount());
            detail.setHandleStatus(HandleStatus.UNHANDLED.name());
            detail.setCreatedAt(LocalDateTime.now());

            if (local == null) {
                detail.setOrderNo(row.orderNo());
                detail.setDiffType(DiffType.CHANNEL_ONLY.name());
                diffCount++;
                diffAmount = diffAmount.add(row.amount());
            } else {
                usedLocalIds.add(local.getId());
                detail.setOrderNo(local.getOrderNo());
                detail.setLocalAmount(local.getAmount());
                if (row.amount().compareTo(local.getAmount()) == 0) {
                    detail.setDiffType(DiffType.MATCH.name());
                    detail.setHandleStatus(HandleStatus.HANDLED.name());
                    matched++;
                } else {
                    detail.setDiffType(DiffType.AMOUNT_DIFF.name());
                    diffCount++;
                    diffAmount = diffAmount.add(row.amount().subtract(local.getAmount()).abs());
                }
            }
            detailMapper.insert(detail);
        }

        for (PaymentOrder local : locals) {
            if (usedLocalIds.contains(local.getId())) {
                continue;
            }
            ReconciliationDetail detail = new ReconciliationDetail();
            detail.setBatchId(batch.getId());
            detail.setOrderNo(local.getOrderNo());
            detail.setChannelTradeNo(local.getTransactionNo());
            detail.setLocalAmount(local.getAmount());
            detail.setDiffType(DiffType.LOCAL_ONLY.name());
            detail.setHandleStatus(HandleStatus.UNHANDLED.name());
            detail.setCreatedAt(LocalDateTime.now());
            detailMapper.insert(detail);
            diffCount++;
            diffAmount = diffAmount.add(local.getAmount());
        }

        batch.setTotalCount(rows.size());
        batch.setTotalAmount(totalAmount);
        batch.setMatchedCount(matched);
        batch.setDiffCount(diffCount);
        batch.setDiffAmount(diffAmount);
        // 无差异直接归档；有差异待财务逐笔核销
        batch.setStatus(diffCount == 0 ? "FINISHED" : "PROCESSING");
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return toBatchVO(batch);
    }

    @Override
    public PageResult<ReconDetailVO> details(String batchNo, String diffType, PageQuery query) {
        ReconciliationBatch batch = requireBatch(batchNo);
        IPage<ReconciliationDetail> page = detailMapper.selectPage(query.toPage(),
                Wrappers.<ReconciliationDetail>lambdaQuery()
                        .eq(ReconciliationDetail::getBatchId, batch.getId())
                        .eq(StringUtils.hasText(diffType), ReconciliationDetail::getDiffType, diffType)
                        .orderByAsc(ReconciliationDetail::getId));
        return PageResult.of(page.convert(this::toDetailVO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long detailId, ReconDetailHandleDTO dto) {
        ReconciliationDetail detail = detailMapper.selectById(detailId);
        if (detail == null) {
            throw new BusinessException(ResultCode.DETAIL_NOT_FOUND);
        }
        ReconciliationBatch batch = batchMapper.selectById(detail.getBatchId());
        if (batch == null) {
            throw new BusinessException(ResultCode.BATCH_NOT_FOUND);
        }
        if ("FINISHED".equals(batch.getStatus())) {
            throw new BusinessException(ResultCode.BATCH_STATUS_INVALID, "批次已归档，不允许再修改");
        }
        HandleStatus status = HandleStatus.of(dto.getHandleStatus());
        if (status == null || status == HandleStatus.UNHANDLED) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "处理状态只能为 HANDLED 或 IGNORED");
        }
        detail.setHandleStatus(status.name());
        detail.setHandleRemark(dto.getHandleRemark());
        detailMapper.updateById(detail);

        Long remaining = detailMapper.selectCount(Wrappers.<ReconciliationDetail>lambdaQuery()
                .eq(ReconciliationDetail::getBatchId, batch.getId())
                .ne(ReconciliationDetail::getDiffType, DiffType.MATCH.name())
                .eq(ReconciliationDetail::getHandleStatus, HandleStatus.UNHANDLED.name()));
        if (remaining != null && remaining == 0) {
            batch.setStatus("FINISHED");
            batch.setUpdatedAt(LocalDateTime.now());
            batchMapper.updateById(batch);
        }
    }

    private List<BillRow> parseBill(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "账单文件读取失败");
        }
        List<BillRow> rows = new ArrayList<>();
        boolean headerSkipped = false;
        for (int i = 0; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = lines.get(i).replace("\uFEFF", "").trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!headerSkipped && (line.contains("订单号") || line.toLowerCase().contains("order"))) {
                headerSkipped = true;
                continue;
            }
            headerSkipped = true;
            String[] cols = line.split(",", -1);
            if (cols.length < 3) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "账单第 " + lineNo + " 行格式错误，应为 订单号,渠道流水号,金额");
            }
            String orderNo = cols[0].trim();
            String channelTradeNo = cols[1].trim();
            String amountText = String.join("", java.util.Arrays.copyOfRange(cols, 2, cols.length))
                    .replace("¥", "").replace("￥", "").trim();
            BigDecimal amount;
            try {
                amount = new BigDecimal(amountText).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "账单第 " + lineNo + " 行金额格式错误");
            }
            if (!StringUtils.hasText(orderNo) && !StringUtils.hasText(channelTradeNo)) {
                throw new BusinessException(ResultCode.PARAM_INVALID,
                        "账单第 " + lineNo + " 行订单号与渠道流水号不能同时为空");
            }
            rows.add(new BillRow(orderNo, channelTradeNo, amount));
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "账单文件无有效数据行");
        }
        return rows;
    }

    private ReconciliationBatch requireBatch(String batchNo) {
        ReconciliationBatch batch = batchMapper.selectOne(Wrappers.<ReconciliationBatch>lambdaQuery()
                .eq(ReconciliationBatch::getBatchNo, batchNo));
        if (batch == null) {
            throw new BusinessException(ResultCode.BATCH_NOT_FOUND);
        }
        return batch;
    }

    private ReconBatchVO toBatchVO(ReconciliationBatch batch) {
        ReconBatchVO vo = new ReconBatchVO();
        BeanUtils.copyProperties(batch, vo);
        return vo;
    }

    private ReconDetailVO toDetailVO(ReconciliationDetail detail) {
        ReconDetailVO vo = new ReconDetailVO();
        BeanUtils.copyProperties(detail, vo);
        return vo;
    }

    private record BillRow(String orderNo, String channelTradeNo, BigDecimal amount) {
    }
}
