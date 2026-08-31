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
     * 更新用户的节点关联
     *
     * @param userId 用户ID
     * @param nodeId 节点ID（可为null表示解除关联）
     */
    void updateNodeId(Long userId, Long nodeId);
}
