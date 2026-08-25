package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;

/**
 * 家族留言服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyMessageService {

    /**
     * 发布留言
     *
     * @param familyId 家族ID
     * @param userId   当前用户ID
     * @param username 当前用户名
     * @param dto      留言内容
     */
    void postMessage(Long familyId, Long userId, String username, MessageCreateDTO dto);

    /**
     * 分页查询留言列表（按时间倒序）
     *
     * @param familyId     家族ID
     * @param currentUserId 当前用户ID（用于标记 own）
     * @param page         页码
     * @param size         每页大小
     * @return 分页结果
     */
    PageResult<MessageVO> listMessages(Long familyId, Long currentUserId, int page, int size);

    /**
     * 删除留言（仅作者本人可删除）
     *
     * @param messageId 留言ID
     * @param userId    当前用户ID
     */
    void deleteMessage(Long messageId, Long userId);
}
