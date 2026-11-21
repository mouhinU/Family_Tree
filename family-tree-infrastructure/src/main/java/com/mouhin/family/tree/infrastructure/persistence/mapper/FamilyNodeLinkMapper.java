package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨族节点链接 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Mapper
public interface FamilyNodeLinkMapper extends BaseMapper<FamilyNodeLinkDO> {
}
