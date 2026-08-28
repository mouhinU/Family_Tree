package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 智能录入请求对象
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Getter
@Setter
public class AiSmartEntryDTO {

    @NotBlank(message = "描述内容不能为空")
    @Size(max = 5000, message = "描述内容不能超过5000字")
    private String description;
}
