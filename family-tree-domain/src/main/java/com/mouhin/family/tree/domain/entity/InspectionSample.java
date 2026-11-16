package com.mouhin.family.tree.domain.entity;

import java.util.Objects;

/**
 * 巡检异常样本：一条异常记录的最小可定位信息。
 *
 * <p>只承载定位所需的标识与说明文本，不含任何业务行为，也不暴露联系方式原文。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class InspectionSample {

    /**
     * 异常记录所在行的主键
     */
    private final Long refId;

    /**
     * 异常主体的可读名称（账号名 / 家族名 / 节点名等）
     */
    private final String subject;

    /**
     * 异常细节描述
     */
    private final String detail;

    public InspectionSample(Long refId, String subject, String detail) {
        this.refId = refId;
        this.subject = subject;
        this.detail = detail;
    }

    public Long getRefId() {
        return refId;
    }

    public String getSubject() {
        return subject;
    }

    public String getDetail() {
        return detail;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InspectionSample)) {
            return false;
        }
        InspectionSample other = (InspectionSample) obj;
        return Objects.equals(refId, other.refId) && Objects.equals(subject, other.subject)
                && Objects.equals(detail, other.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refId, subject, detail);
    }

    @Override
    public String toString() {
        return "InspectionSample{refId=" + refId + ", subject='" + subject + "', detail='" + detail + "'}";
    }
}
