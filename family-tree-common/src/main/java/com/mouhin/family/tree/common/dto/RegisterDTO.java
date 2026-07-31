package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 注册请求对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class RegisterDTO {

    private String username;
    private String password;
    private String nickname;
}
