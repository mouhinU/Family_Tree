package com.mouhin.family.tree.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 私信发送请求对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
public class PrivateMessageDTO {

    /**
     * 接收者用户ID
     */
    @NotNull(message = "请选择收信人")
    private Long receiverId;

    /**
     * 消息内容
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 500, message = "消息内容不能超过500个字符")
    private String content;
}
