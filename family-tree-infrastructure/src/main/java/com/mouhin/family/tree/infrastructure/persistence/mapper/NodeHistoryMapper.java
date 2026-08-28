package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 节点历史 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Mapper
public interface NodeHistoryMapper extends BaseMapper<NodeHistoryDO> {

    /**
     * 获取节点下一个版本号
     *
     * @param nodeId 节点ID
     * @return 下一个版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) + 1 FROM family_node_history WHERE node_id = #{nodeId}")
    int getNextVersion(@Param("nodeId") Long nodeId);
}
