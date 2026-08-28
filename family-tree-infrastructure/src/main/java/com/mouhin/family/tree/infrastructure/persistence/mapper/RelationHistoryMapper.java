package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.RelationHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 关系历史 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Mapper
public interface RelationHistoryMapper extends BaseMapper<RelationHistoryDO> {

    /**
     * 获取关系下一个版本号
     *
     * @param relationId 关系ID
     * @return 下一个版本号
     */
    @Select("SELECT COALESCE(MAX(version_number), 0) + 1 FROM family_relation_history WHERE relation_id = #{relationId}")
    int getNextVersion(@Param("relationId") Long relationId);
}
