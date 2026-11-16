package com.mouhin.family.tree.domain.entity;

import java.util.Collections;
import java.util.List;

/**
 * 单个巡检项的探测结果：异常总数与若干样本明细。
 *
 * <p>总数来自数据库侧的窗口计数，样本按主键升序截断，因此样本数可能小于总数。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class InspectionFinding {

    /**
     * 无异常时的空结果，避免调用方重复构造
     */
    private static final InspectionFinding EMPTY = new InspectionFinding(0L, Collections.emptyList());

    /**
     * 命中的异常记录总数
     */
    private final long total;

    /**
     * 异常样本明细
     */
    private final List<InspectionSample> samples;

    public InspectionFinding(long total, List<InspectionSample> samples) {
        this.total = total;
        this.samples = samples == null ? Collections.emptyList() : List.copyOf(samples);
    }

    /**
     * 构造一个无异常结果
     *
     * @return 总数为 0、样本为空的结果
     */
    public static InspectionFinding empty() {
        return EMPTY;
    }

    public long getTotal() {
        return total;
    }

    public List<InspectionSample> getSamples() {
        return samples;
    }

    @Override
    public String toString() {
        return "InspectionFinding{total=" + total + ", samples=" + samples.size() + "}";
    }
}
