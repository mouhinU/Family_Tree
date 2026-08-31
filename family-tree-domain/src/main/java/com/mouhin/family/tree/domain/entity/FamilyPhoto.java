package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 家族相册照片实体
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class FamilyPhoto {

    public static final int MAX_TITLE_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    private Long id;
    private Long familyId;
    private Long userId;
    private String username;
    private String title;
    private String description;
    private String photoUrl;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 创建时校验标题与描述
     */
    public void validateForCreate() {
        if (title == null || title.isBlank()) {
            throw new BusinessException("照片标题不能为空");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("照片标题不能超过" + MAX_TITLE_LENGTH + "个字符");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("照片描述不能超过" + MAX_DESCRIPTION_LENGTH + "个字符");
        }
        if (photoUrl == null || photoUrl.isBlank()) {
            throw new BusinessException("照片文件不能为空");
        }
    }

    /**
     * 判断指定用户是否为照片上传者
     *
     * @param userId 用户ID
     * @return 是否为上传者
     */
    public boolean isUploader(Long userId) {
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
        FamilyPhoto that = (FamilyPhoto) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "FamilyPhoto{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", title='" + title + '\''
                + '}';
    }
}
