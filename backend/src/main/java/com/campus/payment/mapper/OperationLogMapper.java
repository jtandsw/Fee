package com.campus.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.payment.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
