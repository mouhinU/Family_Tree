package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族成员数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
@TableName("family_member")
public class FamilyMemberDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色：OWNER / MEMBER
     */
    private String role;

    /**
     * 加入时间
     */
    private LocalDateTime joinedTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
