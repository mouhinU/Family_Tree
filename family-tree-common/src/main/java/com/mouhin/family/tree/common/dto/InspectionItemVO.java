package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单项数据巡检结果视图对象
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class InspectionItemVO {

    /**
     * 巡检项编码
     */
    private String code;

    /**
     * 巡检项名称
     */
    private String title;

    /**
     * 巡检项说明
     */
    private String description;

    /**
     * 严重级别编码：HIGH / MEDIUM / LOW
     */
    private String severity;

    /**
     * 严重级别中文名
     */
    private String severityLabel;

    /**
     * 该巡检项在全库命中的异常记录总数
     */
    private Long total;

    /**
     * 样本列表是否被截断（总数大于样本数时为 true）
     */
    private Boolean truncated;

    /**
     * 异常记录样本，最多 {@code sampleLimit} 条
     */
    private List<InspectionSampleVO> samples;

    @Override
    public String toString() {
        return "InspectionItemVO{"
                + "code='" + code + '\''
                + ", severity='" + severity + '\''
                + ", total=" + total
                + ", truncated=" + truncated
                + ", sampleCount=" + (samples == null ? 0 : samples.size())
                + '}';
    }
}
