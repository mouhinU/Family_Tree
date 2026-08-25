package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyGenerationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 族谱辈分 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Mapper
public interface FamilyGenerationMapper extends BaseMapper<FamilyGenerationDO> {
}
