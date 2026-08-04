package com.mouhin.family.tree.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.persistence.entity.OperationLogDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作日志 Mapper
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLogDO> {
}
