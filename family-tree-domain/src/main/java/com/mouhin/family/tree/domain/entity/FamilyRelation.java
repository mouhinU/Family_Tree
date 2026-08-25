package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 族谱关系实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyRelation {

    private Long id;
    private Long userId;
    private Long familyId;
    private Long fromNodeId;
    private Long toNodeId;
    private Integer relationType;
    private LocalDate marriageDate;
    private LocalDate divorceDate;
    private Boolean divorced;
    private Boolean widowed;
    private Integer marriageOrder;
    private String endType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 判断是否为亲子关系
     *
     * @return 是否为亲子关系
     */
    public boolean isParentChild() {
        return Objects.equals(relationType, RelationTypeEnum.PARENT_CHILD.getCode());
    }

    /**
     * 判断是否为夫妻关系
     *
     * @return 是否为夫妻关系
     */
    public boolean isSpouse() {
        return Objects.equals(relationType, RelationTypeEnum.SPOUSE.getCode());
    }

    /**
     * 判断是否为过继/收养关系
     *
     * @return 是否为过继/收养关系
     */
    public boolean isAdoption() {
        return Objects.equals(relationType, RelationTypeEnum.ADOPTION.getCode());
    }

    /**
     * 校验结婚日期与离异日期的先后顺序
     */
    public void validateMarriageDates() {
        if (marriageDate != null && divorceDate != null
                && divorceDate.isBefore(marriageDate)) {
            throw new BusinessException("离异日期不能早于结婚日期");
        }
    }

    /**
     * 判断是否为有效婚姻（未离异且未丧偶）
     *
     * @return 是否为有效婚姻
     */
    public boolean isActiveMarriage() {
        return !Boolean.TRUE.equals(divorced) && !Boolean.TRUE.equals(widowed);
    }

    /**
     * 获取配偶节点ID（给定一方，返回另一方）
     *
     * @param nodeId 已知节点ID
     * @return 配偶节点ID
     */
    public Long getSpouseId(Long nodeId) {
        return Objects.equals(fromNodeId, nodeId) ? toNodeId : fromNodeId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyRelation that = (FamilyRelation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyRelation{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", fromNodeId=" + fromNodeId
                + ", toNodeId=" + toNodeId
                + ", relationType=" + relationType
                + ", divorced=" + divorced
                + ", widowed=" + widowed
                + '}';
    }
}
