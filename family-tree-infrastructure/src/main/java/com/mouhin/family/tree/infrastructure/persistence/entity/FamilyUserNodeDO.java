package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户-家族-族谱节点绑定数据对象
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Getter
@Setter
@TableName("family_user_node")
public class FamilyUserNodeDO {

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
     * 绑定的族谱节点ID
     */
    private Long nodeId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
