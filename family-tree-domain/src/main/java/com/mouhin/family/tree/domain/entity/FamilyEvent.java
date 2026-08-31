package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.EventStatusEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族活动实体（聚会组织、报名与费用 AA 的聚合根）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class FamilyEvent {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_LOCATION_LENGTH = 200;

    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String title;
    private String description;
    private LocalDateTime eventTime;
    private String location;
    private BigDecimal totalCost;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建时校验必填字段
     */
    public void validateForCreate() {
        if (title == null || title.isBlank()) {
            throw new BusinessException("活动标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("活动标题不能超过" + MAX_TITLE_LENGTH + "个字符");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("活动说明不能超过" + MAX_DESCRIPTION_LENGTH + "个字符");
        }
        if (location != null && location.length() > MAX_LOCATION_LENGTH) {
            throw new BusinessException("活动地点不能超过" + MAX_LOCATION_LENGTH + "个字符");
        }
        if (eventTime == null) {
            throw new BusinessException("活动时间不能为空");
        }
        if (totalCost != null && totalCost.signum() < 0) {
            throw new BusinessException("活动总费用不能为负数");
        }
    }

    /**
     * 判断活动是否处于报名中
     *
     * @return 是否报名中
     */
    public boolean isOpen() {
        return EventStatusEnum.OPEN.getCode().equals(status);
    }

    /**
     * 判断指定用户是否为活动发起人
     *
     * @param userId 用户ID
     * @return 是否为发起人
     */
    public boolean isCreator(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyEvent that = (FamilyEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyEvent{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", title='" + title + '\''
                + ", status='" + status + '\''
                + '}';
    }
}
