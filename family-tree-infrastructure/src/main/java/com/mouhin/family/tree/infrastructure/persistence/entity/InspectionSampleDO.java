package com.mouhin.family.tree.infrastructure.persistence.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * 巡检探测的窄查询结果对象
 * <p>
 * 承载"异常定位信息 + 窗口计数"，替代 {@code Map} 输出以符合 ORM 映射规约；
 * 不承载联系方式等敏感列，样本仅保留定位所需的名称与说明。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class InspectionSampleDO {

    /**
     * 异常记录所在行的主键
     */
    private Long refId;

    /**
     * 异常主体的可读名称
     */
    private String subject;

    /**
     * 异常细节描述
     */
    private String detail;

    /**
     * 该巡检项的异常总数（窗口计数，样本可能被 LIMIT 截断）
     */
    private Long total;

    @Override
    public String toString() {
        return "InspectionSampleDO{"
                + "refId=" + refId
                + ", subject='" + subject + '\''
                + ", detail='" + detail + '\''
                + ", total=" + total
                + '}';
    }
}
