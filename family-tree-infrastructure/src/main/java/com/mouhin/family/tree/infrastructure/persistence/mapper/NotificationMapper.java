package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.NotificationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
}
