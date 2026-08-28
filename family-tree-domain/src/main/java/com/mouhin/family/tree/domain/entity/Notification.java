package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通知实体
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Getter
@Setter
public class Notification {

    /**
     * 通知ID
     */
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 接收通知的用户ID
     */
    private Long userId;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 通知类型（MEMBER_JOIN / NODE_CREATE / SYSTEM）
     */
    private String notificationType;

    /**
     * 关联对象ID（如节点ID、用户ID）
     */
    private Long relatedId;

    /**
     * 是否已读
     */
    private Boolean read;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Notification that = (Notification) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Notification{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", userId=" + userId
                + ", title='" + title + '\''
                + ", content='" + content + '\''
                + ", notificationType='" + notificationType + '\''
                + ", relatedId=" + relatedId
                + ", read=" + read
                + '}';
    }
}
