package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 用户聚合根实体
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class User {

    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MAX_NICKNAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private Long id;
    private String username;
    private String passwordHash;
    private String nickname;
    private Integer generation;
    private String birthDate;
    private Long nodeId;
    private Long currentFamilyId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 注册时校验用户数据
     */
    public void validateForRegister() {
        if (username == null || username.isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (username.trim().length() > MAX_USERNAME_LENGTH) {
            throw new BusinessException("用户名不能超过" + MAX_USERNAME_LENGTH + "个字符");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new BusinessException("密码不能为空");
        }
    }

    /**
     * 更新用户资料
     *
     * @param nickname   昵称
     * @param birthDate  出生日期
     * @param generation 世代
     */
    public void updateProfile(String nickname, String birthDate, Integer generation) {
        if (nickname != null && !nickname.isBlank()) {
            if (nickname.length() > MAX_NICKNAME_LENGTH) {
                throw new BusinessException("昵称不能超过" + MAX_NICKNAME_LENGTH + "个字符");
            }
            this.nickname = nickname;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (generation != null) {
            this.generation = generation;
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
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "User{"
                + "id=" + id
                + ", username='" + username + '\''
                + ", nickname='" + nickname + '\''
                + ", generation=" + generation
                + ", nodeId=" + nodeId
                + ", currentFamilyId=" + currentFamilyId
                + '}';
    }
}
