package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 族谱节点实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyNode {

    private Long id;
    private Long userId;
    private Long familyId;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private LocalDate deathDate;
    private Integer generation;
    private Integer birthOrder;
    private String colorLabel;
    private String avatar;
    private String remark;
    private String lunarBirthDate;
    private String lunarDeathDate;
    private String zi;
    private String hao;
    private String hui;
    private String graveLocation;
    private String spouseName;
    private String spouseOriginFamily;
    private String biography;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建时校验必填字段
     */
    public void validateForCreate() {
        if (name == null || name.isBlank()) {
            throw new BusinessException("节点名称不能为空");
        }
        if (name.trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException(
                    "节点名称不能超过" + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
        }
        validateGender();
        validateBirthDeathOrder();
        validateRemark();
    }

    /**
     * 更新时校验
     */
    public void validateForUpdate() {
        if (name != null && !name.isBlank()
                && name.trim().length() > FamilyTreeConsts.MAX_NAME_LENGTH) {
            throw new BusinessException(
                    "节点名称不能超过" + FamilyTreeConsts.MAX_NAME_LENGTH + "个字符");
        }
        validateGender();
        validateBirthDeathOrder();
        validateRemark();
    }

    /**
     * 判断节点是否已去世
     *
     * @return 是否有去世日期
     */
    public boolean isDeceased() {
        return deathDate != null;
    }

    /**
     * 校验性别值有效性
     */
    private void validateGender() {
        if (gender != null && (gender < 0 || gender > 2)) {
            throw new BusinessException("性别值无效，应为 0（未知）、1（男）或 2（女）");
        }
    }

    /**
     * 校验生卒日期顺序
     */
    private void validateBirthDeathOrder() {
        if (birthDate != null && deathDate != null && deathDate.isBefore(birthDate)) {
            throw new BusinessException("去世日期不能早于出生日期");
        }
    }

    /**
     * 校验备注长度
     */
    private void validateRemark() {
        if (remark != null && remark.length() > FamilyTreeConsts.MAX_REMARK_LENGTH) {
            throw new BusinessException(
                    "备注不能超过" + FamilyTreeConsts.MAX_REMARK_LENGTH + "个字符");
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
        FamilyNode that = (FamilyNode) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyNode{"
                + "id=" + id
                + ", userId=" + userId
                + ", familyId=" + familyId
                + ", name='" + name + '\''
                + ", gender=" + gender
                + ", generation=" + generation
                + ", birthOrder=" + birthOrder
                + '}';
    }
}
