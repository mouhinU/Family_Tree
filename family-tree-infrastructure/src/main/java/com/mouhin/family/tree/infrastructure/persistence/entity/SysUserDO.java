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
     * 性别（0未知 1男 2女，与 family_node.gender 同口径）
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 农历出生日期（由阳历推导，个人信息记录两个日期）
     */
    private String lunarBirthDate;

    /**
     * 出生日期录入历法（1阳历 2阴历，null 未记录）
     */
    private Integer birthCalendar;

    /**
     * 当前激活的家族ID（多家族管理）
     */
    private Long currentFamilyId;

    /**
     * 是否族谱节点自动开通的账号（区别于真人注册账号，删除节点时据此回收）
     */
    @TableField("is_auto_account")
    private Boolean autoAccount;

    /**
     * 是否需要在下次登录时强制修改密码（一次性初始口令场景为 true）
     */
    @TableField("must_change_password")
    private Boolean mustChangePassword;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
