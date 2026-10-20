package com.campus.payment.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应结构（README 10.2）。
 */
@Data
@Schema(description = "分页数据")
public class PageResult<T> implements Serializable {

    @Schema(description = "当前页数据")
    private List<T> list = new ArrayList<>();

    @Schema(description = "总条数")
    private long total;

    @Schema(description = "页码")
    private long pageNum;

    @Schema(description = "每页条数")
    private long pageSize;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.list = page.getRecords();
        result.total = page.getTotal();
        result.pageNum = page.getCurrent();
        result.pageSize = page.getSize();
        return result;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long pageNum, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.list = list;
        result.total = total;
        result.pageNum = pageNum;
        result.pageSize = pageSize;
        return result;
    }
}
