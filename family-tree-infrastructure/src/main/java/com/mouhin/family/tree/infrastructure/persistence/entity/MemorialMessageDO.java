package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 祭堂缅怀留言数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_memorial_message")
public class MemorialMessageDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 已故节点ID
     */
    private Long nodeId;

    /**
     * 留言用户ID
     */
    private Long userId;

    /**
     * 留言用户名
     */
    private String username;

    /**
     * 缅怀留言内容
     */
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
