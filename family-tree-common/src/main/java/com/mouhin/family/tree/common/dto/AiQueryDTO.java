package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 自然语言查询请求对象
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Data
public class AiQueryDTO {

    @NotBlank(message = "问题不能为空")
    @Size(max = 500, message = "问题不能超过500字")
    private String question;
}
