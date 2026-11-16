package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 平台数据巡检报告视图对象
 *
 * <p>巡检只读，不修改任何数据；报告仅说明"哪些数据不符合预期"，修复需人工确认。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class InspectionReportVO {

    /**
     * 报告生成时间
     */
    private LocalDateTime generatedTime;

    /**
     * 巡检项总数
     */
    private Integer checkCount;

    /**
     * 命中异常的巡检项数量
     */
    private Integer problemCheckCount;

    /**
     * 高严重级别命中的异常记录数
     */
    private Long highCount;

    /**
     * 中严重级别命中的异常记录数
     */
    private Long mediumCount;

    /**
     * 低严重级别命中的异常记录数
     */
    private Long lowCount;

    /**
     * 每个巡检项返回的样本条数上限
     */
    private Integer sampleLimit;

    /**
     * 各巡检项明细
     */
    private List<InspectionItemVO> items;

    @Override
    public String toString() {
        return "InspectionReportVO{"
                + "generatedTime=" + generatedTime
                + ", checkCount=" + checkCount
                + ", problemCheckCount=" + problemCheckCount
                + ", highCount=" + highCount
                + ", mediumCount=" + mediumCount
                + ", lowCount=" + lowCount
                + ", sampleLimit=" + sampleLimit
                + '}';
    }
}
