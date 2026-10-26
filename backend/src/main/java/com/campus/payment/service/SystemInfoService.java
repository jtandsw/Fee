package com.campus.payment.service;

import com.campus.payment.vo.SystemInfoVO;

/**
 * 系统信息（管理员专用）：只输出运行环境与账号规模，不涉及财务业务数据。
 */
public interface SystemInfoService {

    SystemInfoVO info();
}
