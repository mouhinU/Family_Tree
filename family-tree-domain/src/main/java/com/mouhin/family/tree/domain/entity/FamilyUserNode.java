package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户-家族-族谱节点绑定实体（"标记为我"）
 *
 * <p>绑定关系按家族隔离：同一用户在不同家族可绑定不同节点，
 * 因此 (familyId, userId) 唯一，nodeId 为该家族内对应的族谱节点。</p>
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Getter
@Setter
public class FamilyUserNode {

    private Long id;
    private Long familyId;
    private Long userId;
    private Long nodeId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验绑定数据的完整性
     */
    public void validate() {
        if (familyId == null) {
            throw new BusinessException("绑定关系缺少家族ID");
        }
        if (userId == null) {
            throw new BusinessException("绑定关系缺少用户ID");
        }
        if (nodeId == null) {
            throw new BusinessException("绑定关系缺少族谱节点ID");
        }
    }

    /**
     * 判断绑定是否指向指定节点
     *
     * @param targetNodeId 待比对的节点ID
     * @return 是否指向该节点
     */
    public boolean isBoundTo(Long targetNodeId) {
        return Objects.equals(nodeId, targetNodeId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyUserNode that = (FamilyUserNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyUserNode{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", userId=" + userId
                + ", nodeId=" + nodeId
                + '}';
    }
}
