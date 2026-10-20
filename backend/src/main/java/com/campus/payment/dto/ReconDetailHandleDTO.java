package com.campus.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReconDetailHandleDTO {

    /** HANDLED 已处理 / IGNORED 忽略 */
    @NotBlank(message = "处理状态不能为空")
    private String handleStatus;

    @Size(max = 255, message = "处理说明最长 255 字")
    private String handleRemark;
}
