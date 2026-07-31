package com.mouhin.family.tree.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.persistence.entity.SysUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统用户 Mapper
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUserDO> {
}
