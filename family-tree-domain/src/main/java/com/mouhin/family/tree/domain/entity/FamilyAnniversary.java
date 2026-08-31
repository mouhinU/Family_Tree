package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族纪念日实体（结婚周年、入学等自定义纪念日）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class FamilyAnniversary {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_REMARK_LENGTH = 500;

    private Long id;
    private Long familyId;
    private Long nodeId;
    private Long userId;
    private String title;
    private String category;
    private LocalDate anniversaryDate;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建时校验必填字段
     */
    public void validateForCreate() {
        if (title == null || title.isBlank()) {
            throw new BusinessException("纪念日标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("纪念日标题不能超过" + MAX_TITLE_LENGTH + "个字符");
        }
        if (anniversaryDate == null) {
            throw new BusinessException("纪念日日期不能为空");
        }
        if (remark != null && remark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException("备注不能超过" + MAX_REMARK_LENGTH + "个字符");
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
        FamilyAnniversary that = (FamilyAnniversary) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyAnniversary{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", title='" + title + '\''
                + ", anniversaryDate=" + anniversaryDate
                + '}';
    }
}
