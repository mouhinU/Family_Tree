package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {
}
