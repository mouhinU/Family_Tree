package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyRelationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 族谱关系 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Mapper
public interface FamilyRelationMapper extends BaseMapper<FamilyRelationDO> {
}
