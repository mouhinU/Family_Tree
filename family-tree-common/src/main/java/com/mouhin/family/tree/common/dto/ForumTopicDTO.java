package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 论坛主题发布请求对象（content 为富文本 HTML，服务端白名单清洗）
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class ForumTopicDTO {

    /**
     * 主题标题
     */
    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    /**
     * 富文本内容
     */
    @NotBlank(message = "内容不能为空")
    private String content;
}
