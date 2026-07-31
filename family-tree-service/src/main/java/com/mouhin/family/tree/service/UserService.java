package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.LoginDTO;
import com.mouhin.family.tree.common.dto.RegisterDTO;

/**
 * 用户服务接口
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public interface UserService {

    /**
     * 用户注册
     *
     * @param dto 注册信息
     * @return 新用户ID
     */
    Long register(RegisterDTO dto);

    /**
     * 用户登录验证
     *
     * @param dto 登录信息
     * @return 用户ID，验证失败抛出 BusinessException
     */
    Long login(LoginDTO dto);

    /**
     * 根据ID获取用户昵称
     *
     * @param userId 用户ID
     * @return 昵称
     */
    String getNickname(Long userId);

    /**
     * 根据ID获取用户所属辈分（第几世）
     *
     * @param userId 用户ID
     * @return 辈分（第几世），未设置返回 null
     */
    Integer getGeneration(Long userId);
}
