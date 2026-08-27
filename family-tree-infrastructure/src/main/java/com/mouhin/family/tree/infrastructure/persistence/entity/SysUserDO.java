package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
@TableName("sys_user")
public class SysUserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    /**
     * 所属辈分（第几世），用于水印高亮等展示
     */
    private Integer generation;

    /**
     * 出生日期
     */
    private String birthDate;

    /**
     * 关联族谱节点ID（标记当前用户在族谱中的位置）
     */
    private Long nodeId;

    /**
     * 当前激活的家族ID（多家族管理）
     */
    private Long currentFamilyId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
