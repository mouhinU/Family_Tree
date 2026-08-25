package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 留言展示对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
public class MessageVO {

    /** 留言ID */
    private Long id;

    /** 留言用户ID */
    private Long userId;

    /** 留言用户名 */
    private String username;

    /** 留言内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 是否为当前用户（用于前端判断删除权限） */
    private Boolean own;
}
