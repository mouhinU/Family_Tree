package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族留言 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Mapper
public interface FamilyMessageMapper extends BaseMapper<FamilyMessageDO> {
}
