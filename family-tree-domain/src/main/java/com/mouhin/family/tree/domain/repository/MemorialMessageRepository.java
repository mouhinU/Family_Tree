package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.domain.entity.MemorialMessage;

import java.util.List;

/**
 * 祭堂缅怀留言仓储接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public interface MemorialMessageRepository {

    /**
     * 保存缅怀留言
     *
     * @param message 留言领域对象
     * @return 保存后的留言（含ID）
     */
    MemorialMessage save(MemorialMessage message);

    /**
     * 根据ID查询留言
     *
     * @param id 留言ID
     * @return 留言领域对象，不存在返回null
     */
    MemorialMessage findById(Long id);

    /**
     * 查询节点的缅怀留言列表（按时间倒序）
     *
     * @param nodeId 已故节点ID
     * @return 留言列表
     */
    List<MemorialMessage> findByNodeId(Long nodeId);

    /**
     * 删除留言
     *
     * @param id 留言ID
     */
    void removeById(Long id);
}
