package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族论坛主题实体（富文本内容入库前须经白名单清洗）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ForumTopic {

    public static final int MAX_TITLE_LENGTH = 100;

    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String title;
    private String content;
    private Long viewCount;
    private Long replyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验标题与内容
     */
    public void validateForCreate() {
        if (title == null || title.isBlank()) {
            throw new BusinessException("主题标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("主题标题不能超过" + MAX_TITLE_LENGTH + "个字符");
        }
        if (content == null || content.isBlank()) {
            throw new BusinessException("主题内容不能为空");
        }
    }

    /**
     * 判断指定用户是否为发帖人
     *
     * @param userId 用户ID
     * @return 是否为发帖人
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
        ForumTopic that = (ForumTopic) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "ForumTopic{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", title='" + title + '\''
                + '}';
    }
}
