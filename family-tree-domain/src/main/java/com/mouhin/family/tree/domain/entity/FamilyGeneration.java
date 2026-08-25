package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族字辈实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyGeneration {

    private Long id;
    private Long userId;
    private Long familyId;
    private Integer generation;
    private String name;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验字辈数据的有效性
     */
    public void validateGeneration() {
        if (generation == null) {
            throw new BusinessException("世代层级不能为空");
        }
        if (generation < 1 || generation > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
            throw new BusinessException(
                    "世代层级必须在1到" + FamilyTreeConsts.MAX_GENERATION_DEPTH + "之间");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessException("字辈名称不能为空");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyGeneration that = (FamilyGeneration) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyGeneration{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", generation=" + generation
                + ", name='" + name + '\''
                + '}';
    }
}
