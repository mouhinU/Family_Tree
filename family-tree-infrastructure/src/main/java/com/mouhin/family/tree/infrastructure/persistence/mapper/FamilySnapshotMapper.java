package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilySnapshotDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族快照 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Mapper
public interface FamilySnapshotMapper extends BaseMapper<FamilySnapshotDO> {
}
