package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨族节点链接提案事件 Mapper
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Mapper
public interface FamilyNodeLinkProposalEventMapper extends BaseMapper<FamilyNodeLinkProposalEventDO> {
}
