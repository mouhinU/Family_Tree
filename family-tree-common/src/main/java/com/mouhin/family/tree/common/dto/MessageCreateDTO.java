package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 留言创建请求
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
public class MessageCreateDTO {

    /**
     * 留言内容
     */
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 500, message = "留言内容不能超过500字")
    private String content;

    /**
     * 留言分类（GENERAL-普通留言, FEATURE-功能需求）
     */
    private String category;

    /**
     * 父留言ID（null 表示顶级留言，非null 表示回复）
     */
    private Long parentId;
}
