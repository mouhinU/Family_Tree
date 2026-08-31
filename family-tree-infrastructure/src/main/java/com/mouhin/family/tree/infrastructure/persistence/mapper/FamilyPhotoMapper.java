package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyPhotoDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 家族相册照片 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Mapper
public interface FamilyPhotoMapper extends BaseMapper<FamilyPhotoDO> {
}
