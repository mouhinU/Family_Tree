package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族活动 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Mapper
public interface FamilyEventMapper extends BaseMapper<FamilyEventDO> {
}
