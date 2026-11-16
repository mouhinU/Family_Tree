package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyCountDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyMemberDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.UserCountDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 家族成员 Mapper 接口
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Mapper
public interface FamilyMemberMapper extends BaseMapper<FamilyMemberDO> {

    /**
     * 按家族分组统计成员数，一次查询覆盖多个家族，避免逐家族往返
     *
     * @param familyIds 家族ID列表，调用方保证非空且不超过 1000 个
     * @return 家族维度计数结果
     */
    @Select("<script>"
            + "SELECT family_id AS familyId, COUNT(*) AS total FROM family_member"
            + " WHERE family_id IN"
            + "<foreach collection='familyIds' item='familyId' open='(' separator=',' close=')'>#{familyId}</foreach>"
            + " GROUP BY family_id"
            + "</script>")
    List<FamilyCountDO> countGroupedByFamilyIds(@Param("familyIds") List<Long> familyIds);

    /**
     * 按用户分组统计其加入的家族数，一次查询覆盖多个用户，避免逐用户往返
     *
     * @param userIds 用户ID列表，调用方保证非空且不超过 1000 个
     * @return 用户维度计数结果
     */
    @Select("<script>"
            + "SELECT user_id AS userId, COUNT(*) AS total FROM family_member"
            + " WHERE user_id IN"
            + "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>#{userId}</foreach>"
            + " GROUP BY user_id"
            + "</script>")
    List<UserCountDO> countGroupedByUserIds(@Param("userIds") List<Long> userIds);
}
