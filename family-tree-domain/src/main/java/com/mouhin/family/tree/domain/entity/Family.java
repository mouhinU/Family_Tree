package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
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

    /**
     * 家族状态编码，取值见 {@link FamilyStatusEnum}
     */
    private String status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验家族名称（创建/更新时调用）
     */
    public void validateName() {
        if (name == null || name.isBlank()) {
            throw new BusinessException("家族名称不能为空");
        }
        if (name.length() > FamilyTreeConsts.MAX_FAMILY_NAME_LENGTH) {
            throw new BusinessException("家族名称不能超过" + FamilyTreeConsts.MAX_FAMILY_NAME_LENGTH + "个字符");
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

    /**
     * 读取家族状态，历史数据未落状态时按正常处理
     *
     * @return 家族状态枚举，不为 null
     */
    public FamilyStatusEnum statusOrDefault() {
        if (status == null || status.isBlank()) {
            return FamilyStatusEnum.NORMAL;
        }
        return FamilyStatusEnum.fromCode(status);
    }

    /**
     * 变更家族状态
     *
     * @param targetStatus 目标状态
     */
    public void changeStatus(FamilyStatusEnum targetStatus) {
        if (targetStatus == null) {
            throw new BusinessException("缺少家族状态");
        }
        this.status = targetStatus.getCode();
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
                + ", status='" + status + '\''
                + '}';
    }
}
