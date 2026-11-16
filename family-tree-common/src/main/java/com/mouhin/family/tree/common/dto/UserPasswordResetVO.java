package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台重置密码结果视图对象
 *
 * <p>一次性口令只在该次响应中出现，服务端不落日志、不再返回。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class UserPasswordResetVO {

    /**
     * 被重置的账号ID
     */
    private Long userId;

    /**
     * 被重置的登录用户名
     */
    private String username;

    /**
     * 被重置账号的昵称
     */
    private String nickname;

    /**
     * 新生成的一次性口令
     */
    private String newPassword;

    /**
     * 该账号是否需要在下次登录时修改密码
     */
    private Boolean mustChangePassword;

    @Override
    public String toString() {
        // 故意不输出 newPassword：视图对象会进入日志，一次性口令不得留痕
        return "UserPasswordResetVO{"
                + "userId=" + userId
                + ", username='" + username + '\''
                + ", mustChangePassword=" + mustChangePassword
                + '}';
    }
}
