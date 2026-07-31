package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 登录请求对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class LoginDTO {

    private String username;
    private String password;
}
