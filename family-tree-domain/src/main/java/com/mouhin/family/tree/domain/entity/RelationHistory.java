package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 关系修改历史实体
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class RelationHistory {

    private Long id;
    private Long relationId;
    private Long familyId;
    private String operationType; // CREATE/UPDATE/DELETE
    private Long operatorId;
    private String operatorName;
    private String beforeData; // JSON格式
    private String afterData; // JSON格式
    private String changeSummary;
    private String ipAddress;
    private LocalDateTime createTime;
    private Integer versionNumber;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RelationHistory that = (RelationHistory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "RelationHistory{"
                + "id=" + id
                + ", relationId=" + relationId
                + ", familyId=" + familyId
                + ", operationType='" + operationType + '\''
                + ", operatorId=" + operatorId
                + ", operatorName='" + operatorName + '\''
                + ", changeSummary='" + changeSummary + '\''
                + ", versionNumber=" + versionNumber
                + ", createTime=" + createTime
                + '}';
    }
}
