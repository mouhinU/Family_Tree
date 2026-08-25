package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.FamilyMessage;

import java.util.List;

/**
 * 家族留言仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
public interface FamilyMessageRepository {

    /**
     * 保存留言（新建）
     *
     * @param message 留言领域对象
     * @return 保存后的留言（含ID）
     */
    FamilyMessage save(FamilyMessage message);

    /**
     * 根据ID查询留言
     *
     * @param id 留言ID
     * @return 留言领域对象，不存在返回null
     */
    FamilyMessage findById(Long id);

    /**
     * 根据家族ID分页查询留言
     *
     * @param familyId 家族ID
     * @param offset   偏移量
     * @param limit    每页数量
     * @return 留言列表
     */
    List<FamilyMessage> findByFamilyId(Long familyId, int offset, int limit);

    /**
     * 统计家族留言数量
     *
     * @param familyId 家族ID
     * @return 留言数量
     */
    long countByFamilyId(Long familyId);

    /**
     * 根据ID删除留言
     *
     * @param id 留言ID
     */
    void removeById(Long id);
}
