package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 操作日志实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class OperationLog {

    private Long id;
    private Long userId;
    private String username;
    private String operationType;
    private String operationDesc;
    private String targetType;
    private Long targetId;
    private Long familyId;
    private String ipAddress;
    private LocalDateTime createTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OperationLog that = (OperationLog) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "OperationLog{"
                + "id=" + id
                + ", userId=" + userId
                + ", username='" + username + '\''
                + ", operationType='" + operationType + '\''
                + ", operationDesc='" + operationDesc + '\''
                + ", targetType='" + targetType + '\''
                + ", targetId=" + targetId
                + ", familyId=" + familyId
                + '}';
    }
}
