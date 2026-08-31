package com.mouhin.family.tree.domain.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 照片人物标记实体（照片与族谱节点的关联）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PhotoTag {

    private Long id;
    private Long photoId;
    private Long nodeId;
    private String nodeName;
    private LocalDateTime createTime;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PhotoTag that = (PhotoTag) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PhotoTag{"
                + "id=" + id
                + ", photoId=" + photoId
                + ", nodeId=" + nodeId
                + ", nodeName='" + nodeName + '\''
                + '}';
    }
}
