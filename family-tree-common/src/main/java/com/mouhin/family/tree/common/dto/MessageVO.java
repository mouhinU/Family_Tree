package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

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

    /** 点赞数 */
    private Long likeCount;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 留言分类编码 */
    private String category;

    /** 留言分类描述 */
    private String categoryDesc;

    /** 父留言ID */
    private Long parentId;

    /** 回复数 */
    private Long replyCount;

    /** 回复列表（仅顶级留言时加载） */
    private List<MessageVO> replies;
}
