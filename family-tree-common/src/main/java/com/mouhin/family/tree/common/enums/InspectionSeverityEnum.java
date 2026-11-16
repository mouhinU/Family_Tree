package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据巡检严重级别
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@AllArgsConstructor
public enum InspectionSeverityEnum {

    HIGH("HIGH", "高", "可能直接导致越权、数据错乱或功能不可用"),
    MEDIUM("MEDIUM", "中", "影响展示与统计口径，需要尽快核对"),
    LOW("LOW", "低", "数据质量问题，不影响功能可用性");

    /**
     * 级别编码
     */
    private final String code;

    /**
     * 级别展示名
     */
    private final String label;

    /**
     * 级别含义说明
     */
    private final String description;
}
