package com.campus.payment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "学生名单导入结果")
public class ImportResultVO {
    private int total;
    private int success;
    private int skipped;
    private int failed;
    private List<String> messages = new ArrayList<>();

    public void addMessage(String message) {
        if (messages.size() < 100) {
            messages.add(message);
        }
    }
}
