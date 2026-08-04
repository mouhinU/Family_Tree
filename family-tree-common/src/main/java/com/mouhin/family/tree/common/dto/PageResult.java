package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页查询结果
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Data
public class PageResult<T> {

    /** 数据列表 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private int page;

    /** 每页大小 */
    private int size;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
