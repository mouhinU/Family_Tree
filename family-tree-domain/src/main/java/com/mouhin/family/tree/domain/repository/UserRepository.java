package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.User;

import java.util.List;

/**
 * 用户仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface UserRepository {

    /**
     * 保存用户（新建）
     *
     * @param user 用户领域对象
     * @return 保存后的用户（含ID）
     */
    User save(User user);

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户领域对象，不存在返回null
     */
    User findById(Long id);

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 用户领域对象，不存在返回null
     */
    User findByUsername(String username);

    /**
     * 根据邮箱查询用户（邮箱全局唯一，可用于登录）
     *
     * @param email 邮箱
     * @return 用户领域对象，不存在返回null
     */
    User findByEmail(String email);

    /**
     * 根据手机号查询用户（手机号全局唯一，可用于登录）
     *
     * @param phone 手机号
     * @return 用户领域对象，不存在返回null
     */
    User findByPhone(String phone);

    /**
     * 根据ID列表批量查询用户
     *
     * @param ids 用户ID列表
     * @return 用户领域对象列表
     */
    List<User> findByIds(List<Long> ids);

    /**
     * 查询当前家族下的全部用户（私信联系人）
     *
     * @param familyId 家族ID
     * @return 用户领域对象列表（按用户名正序）
     */
    List<User> findByCurrentFamilyId(Long familyId);

    /**
     * 更新用户
     *
     * @param user 用户领域对象
     */
    void update(User user);

    /**
     * 根据ID删除用户账号
     *
     * <p>仅用于回收族谱节点自动开通的 fp_ 账号，调用方须先解除其绑定关系。</p>
     *
     * @param id 用户ID
     */
    void deleteById(Long id);
}
