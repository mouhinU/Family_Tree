package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.OfferingTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 祭奠实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyOffering {

    private Long id;
    private Long userId;
    private Long familyId;
    private Long nodeId;
    private Integer offeringType;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验祭奠类型有效性
     */
    public void validateOfferingType() {
        if (offeringType == null) {
            throw new BusinessException("祭奠类型不能为空");
        }
        OfferingTypeEnum.fromCode(offeringType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyOffering that = (FamilyOffering) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyOffering{"
                + "id=" + id
                + ", userId=" + userId
                + ", familyId=" + familyId
                + ", nodeId=" + nodeId
                + ", offeringType=" + offeringType
                + '}';
    }
}
