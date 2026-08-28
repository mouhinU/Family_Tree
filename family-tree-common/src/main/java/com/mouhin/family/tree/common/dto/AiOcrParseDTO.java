package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * AI OCR 解析请求对象
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Getter
@Setter
public class AiOcrParseDTO {

    @NotBlank(message = "识别文本不能为空")
    @Size(max = 10000, message = "识别文本不能超过10000字")
    private String recognizedText;
}
