package com.mouhin.family.tree.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.persistence.entity.FamilyGenerationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 族谱辈分 Mapper
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Mapper
public interface FamilyGenerationMapper extends BaseMapper<FamilyGenerationDO> {
}
