package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统用户数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    /**
     * 邮箱（选填，非空时全局唯一，可用于登录）
     */
    private String email;

    /**
     * 手机号（选填，非空时全局唯一，可用于登录）
     */
    private String phone;

    /**
     * 所属辈分（第几世），用于水印高亮等展示
     */
    private Integer generation;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 当前激活的家族ID（多家族管理）
     */
    private Long currentFamilyId;

    /**
     * 是否族谱节点自动开通的账号（区别于真人注册账号，删除节点时据此回收）
     */
    @TableField("is_auto_account")
    private Boolean autoAccount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
