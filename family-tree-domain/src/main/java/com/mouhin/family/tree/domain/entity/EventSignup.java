package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 活动报名实体
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class EventSignup {

    public static final int MAX_ATTENDEE_COUNT = 20;

    private Long id;
    private Long eventId;
    private Long familyId;
    private Long userId;
    private String username;
    private Integer attendeeCount;
    private String remark;
    private LocalDateTime createTime;

    /**
     * 校验报名人数
     */
    public void validateForCreate() {
        if (attendeeCount == null || attendeeCount < 1) {
            throw new BusinessException("参加人数至少为1人");
        }
        if (attendeeCount > MAX_ATTENDEE_COUNT) {
            throw new BusinessException("单次报名人数不能超过" + MAX_ATTENDEE_COUNT + "人");
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
        EventSignup that = (EventSignup) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "EventSignup{"
                + "id=" + id
                + ", eventId=" + eventId
                + ", userId=" + userId
                + ", attendeeCount=" + attendeeCount
                + '}';
    }
}
