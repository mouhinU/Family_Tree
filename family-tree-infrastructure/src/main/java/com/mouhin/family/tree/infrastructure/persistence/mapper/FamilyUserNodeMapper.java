package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyUserNodeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-家族-族谱节点绑定 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-11-05
 */
@Mapper
public interface FamilyUserNodeMapper extends BaseMapper<FamilyUserNodeDO> {
}
