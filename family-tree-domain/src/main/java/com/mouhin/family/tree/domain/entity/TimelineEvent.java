package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.TimelineEventTypeEnum;
import com.mouhin.family.tree.common.enums.TimelineSourceTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 时间线事件实体
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Getter
@Setter
public class TimelineEvent {

    public static final int MAX_DESCRIPTION_LENGTH = 200;

    private Long id;
    private Long familyId;
    private String sourceType;
    private Long sourceId;
    private String eventType;
    private LocalDate eventDate;
    private String description;
    private Long nodeId;
    private Long relationId;
    private Boolean isNew;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建时校验必填字段
     */
    public void validateForCreate() {
        if (sourceType == null || sourceType.isBlank()) {
            throw new BusinessException("来源类型不能为空");
        }
        TimelineSourceTypeEnum.fromCode(sourceType);
        if (sourceId == null) {
            throw new BusinessException("来源ID不能为空");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException("事件类型不能为空");
        }
        TimelineEventTypeEnum.fromCode(eventType);
        if (eventDate == null) {
            throw new BusinessException("事件日期不能为空");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessException("事件描述不能为空");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("事件描述不能超过" + MAX_DESCRIPTION_LENGTH + "个字符");
        }
        if (familyId == null) {
            throw new BusinessException("家族ID不能为空");
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
        TimelineEvent that = (TimelineEvent) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "TimelineEvent{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", sourceType='" + sourceType + '\''
                + ", eventType='" + eventType + '\''
                + ", eventDate=" + eventDate
                + ", isNew=" + isNew
                + '}';
    }
}
