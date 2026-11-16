package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理页用户列表行视图对象
 *
 * <p>联系方式在平台视图中一律脱敏，原文仅在其所属家族内按成员权限展示。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class UserAdminRowVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 登录用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号（脱敏展示，如 138****0969）
     */
    private String phone;

    /**
     * 邮箱（脱敏展示，如 a****@example.com）
     */
    private String email;

    /**
     * 是否族谱节点自动开通的账号
     */
    private Boolean autoAccount;

    /**
     * 是否需要在下次登录时修改密码
     */
    private Boolean mustChangePassword;

    /**
     * 加入的家族数量
     */
    private Integer familyCount;

    /**
     * 当前激活家族ID，未选择家族时为 null
     */
    private Long currentFamilyId;

    /**
     * 当前激活家族名称
     */
    private String currentFamilyName;

    /**
     * 账号创建时间
     */
    private LocalDateTime createTime;

    /**
     * 账号信息最近更新时间
     */
    private LocalDateTime updateTime;

    @Override
    public String toString() {
        // 故意不输出 phone/email：视图对象会进入日志，脱敏后的联系方式同样无需留痕
        return "UserAdminRowVO{"
                + "id=" + id
                + ", username='" + username + '\''
                + ", nickname='" + nickname + '\''
                + ", autoAccount=" + autoAccount
                + ", familyCount=" + familyCount
                + ", currentFamilyId=" + currentFamilyId
                + '}';
    }
}
