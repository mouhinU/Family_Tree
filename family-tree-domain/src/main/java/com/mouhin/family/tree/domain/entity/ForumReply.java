package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族论坛回复实体
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ForumReply {

    public static final int MAX_CONTENT_LENGTH = 500;

    private Long id;
    private Long topicId;
    private Long familyId;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createTime;

    /**
     * 校验回复内容
     */
    public void validateContent() {
        if (content == null || content.isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("回复内容不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }
    }

    /**
     * 判断指定用户是否为回复作者
     *
     * @param userId 用户ID
     * @return 是否为作者
     */
    public boolean isAuthor(Long userId) {
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
        ForumReply that = (ForumReply) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ForumReply{"
                + "id=" + id
                + ", topicId=" + topicId
                + ", userId=" + userId
                + '}';
    }
}
