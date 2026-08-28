package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族快照实体
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class FamilySnapshot {

    private Long id;
    private Long familyId;
    private String snapshotName;
    private String description;
    private Long creatorId;
    private String creatorName;
    private Integer nodeCount;
    private Integer relationCount;
    private String snapshotData; // JSON格式,包含所有节点和关系
    private LocalDateTime createTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilySnapshot that = (FamilySnapshot) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilySnapshot{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", snapshotName='" + snapshotName + '\''
                + ", creatorId=" + creatorId
                + ", creatorName='" + creatorName + '\''
                + ", nodeCount=" + nodeCount
                + ", relationCount=" + relationCount
                + ", createTime=" + createTime
                + '}';
    }
}
