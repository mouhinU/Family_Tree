package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 数据巡检样本视图对象（单条异常记录的代表性线索）
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class InspectionSampleVO {

    /**
     * 异常记录的主键ID
     */
    private Long refId;

    /**
     * 异常记录的主题描述（如家族名 / 用户名 / 节点名）
     */
    private String subject;

    /**
     * 异常原因的细节描述
     */
    private String detail;

    @Override
    public String toString() {
        return "InspectionSampleVO{"
                + "refId=" + refId
                + ", subject='" + subject + '\''
                + ", detail='" + detail + '\''
                + '}';
    }
}
