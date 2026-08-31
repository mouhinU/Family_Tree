package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.PhotoTagDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 照片人物标记 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Mapper
public interface PhotoTagMapper extends BaseMapper<PhotoTagDO> {
}
