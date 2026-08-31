package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 论坛回复对象（请求与展示复用）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ForumReplyVO {

    /**
     * 回复ID
     */
    private Long id;

    /**
     * 主题ID
     */
    private Long topicId;

    /**
     * 回复用户ID
     */
    private Long userId;

    /**
     * 回复用户名
     */
    private String username;

    /**
     * 回复内容
     */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 500, message = "回复内容不能超过500个字符")
    private String content;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否为当前用户回复（用于前端判断删除权限）
     */
    private Boolean own;
}
