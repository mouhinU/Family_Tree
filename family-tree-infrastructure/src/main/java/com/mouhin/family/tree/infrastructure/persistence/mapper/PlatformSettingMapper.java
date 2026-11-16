package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.PlatformSettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台级配置 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Mapper
public interface PlatformSettingMapper extends BaseMapper<PlatformSettingDO> {
}
