package com.mouhin.family.tree.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.persistence.entity.FamilyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Mapper
public interface FamilyMapper extends BaseMapper<FamilyDO> {
}
