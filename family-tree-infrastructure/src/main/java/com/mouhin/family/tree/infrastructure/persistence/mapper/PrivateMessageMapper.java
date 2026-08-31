package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.PrivateMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私信消息 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Mapper
public interface PrivateMessageMapper extends BaseMapper<PrivateMessageDO> {
}
