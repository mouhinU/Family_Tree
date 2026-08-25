package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族聚合根实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class Family {

    private Long id;
    private String name;
    private String inviteCode;
    private Long creatorId;
    private String hallName;
    private String ancestralHome;
    private Integer generationCols;
    private Integer generationRows;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验家族名称（创建/更新时调用）
     */
    public void validateName() {
        if (name == null || name.isBlank()) {
            throw new BusinessException("家族名称不能为空");
        }
        if (name.length() > 100) {
            throw new BusinessException("家族名称不能超过100个字符");
        }
    }

    /**
     * 判断指定用户是否为家族创建者
     *
     * @param userId 用户ID
     * @return 是否为创建者
     */
    public boolean isCreator(Long userId) {
        return Objects.equals(creatorId, userId);
    }

    /**
     * 更新祠堂名称和祖籍
     *
     * @param hallName      祠堂名称
     * @param ancestralHome 祖籍
     */
    public void updateHallInfo(String hallName, String ancestralHome) {
        this.hallName = hallName;
        this.ancestralHome = ancestralHome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Family family = (Family) o;
        return Objects.equals(id, family.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Family{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", inviteCode='" + inviteCode + '\''
                + ", creatorId=" + creatorId
                + ", hallName='" + hallName + '\''
                + ", ancestralHome='" + ancestralHome + '\''
                + ", generationCols=" + generationCols
                + ", generationRows=" + generationRows
                + '}';
    }
}
