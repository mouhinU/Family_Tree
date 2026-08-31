package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 私信消息实体
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PrivateMessage {

    public static final int MAX_CONTENT_LENGTH = 500;

    private Long id;
    private Long familyId;
    private Long senderId;
    private String senderName;
    private Long receiverId;
    private String content;
    private Boolean read;
    private LocalDateTime createTime;

    /**
     * 校验消息内容
     */
    public void validateContent() {
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("消息内容不能超过" + MAX_CONTENT_LENGTH + "个字符");
        }
        if (Objects.equals(senderId, receiverId)) {
            throw new BusinessException("不能给自己发送私信");
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
        PrivateMessage that = (PrivateMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PrivateMessage{"
                + "id=" + id
                + ", senderId=" + senderId
                + ", receiverId=" + receiverId
                + '}';
    }
}
