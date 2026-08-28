package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族留言实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class FamilyMessage {

    public static final int MAX_CONTENT_LENGTH = 500;

    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String content;
    private Long likeCount;
    private String category;
    private Long parentId;
    private Long replyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验留言内容
     */
    public void validateContent() {
        if (content == null || content.isBlank()) {
            throw new BusinessException("留言内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("留言内容不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }
    }

    /**
     * 判断指定用户是否为留言作者
     *
     * @param userId 用户ID
     * @return 是否为作者
     */
    public boolean isAuthor(Long userId) {
        return Objects.equals(this.userId, userId);
    }

    /**
     * 判断是否为顶级留言（非回复）
     *
     * @return 是否为顶级留言
     */
    public boolean isRootMessage() {
        return parentId == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FamilyMessage that = (FamilyMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyMessage{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", userId=" + userId
                + ", username='" + username + '\''
                + '}';
    }
}
