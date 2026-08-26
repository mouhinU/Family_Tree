package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageLikeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 留言点赞记录 Mapper
 *
 * @author Family-Tree
 * @date 2026-08-26
 */
@Mapper
public interface FamilyMessageLikeMapper extends BaseMapper<FamilyMessageLikeDO> {
}
