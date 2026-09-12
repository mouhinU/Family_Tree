package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.TimelineEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 时间线事件 Mapper
 *
 * @author Family-Tree
 * @date 2026-09-12
 */
@Mapper
public interface TimelineEventMapper extends BaseMapper<TimelineEventDO> {
}
