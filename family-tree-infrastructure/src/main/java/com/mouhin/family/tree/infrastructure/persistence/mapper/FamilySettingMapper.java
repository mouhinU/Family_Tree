package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族级配置 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
@Mapper
public interface FamilySettingMapper extends BaseMapper<FamilySettingDO> {
}
