package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 祭堂缅怀留言展示对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class MemorialMessageVO {

    /**
     * 留言ID
     */
    private Long id;

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

    /**
     * 是否为当前用户留言（用于前端判断删除权限）
     */
    private Boolean own;
}
