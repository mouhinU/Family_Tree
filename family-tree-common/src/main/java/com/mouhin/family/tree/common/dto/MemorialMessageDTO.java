package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 祭堂缅怀留言请求对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class MemorialMessageDTO {

    /**
     * 缅怀留言内容
     */
    @NotBlank(message = "留言内容不能为空")
    @Size(max = 500, message = "留言内容不能超过500个字符")
    private String content;
}
