package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 平台数据巡检 Mapper：每项巡检对应一条只读探测语句。
 *
 * <p>探测语句统一用窗口函数 {@code COUNT(*) OVER ()} 在同一次查询里带回异常总数，
 * 样本按主键升序由 LIMIT 截断，因此样本数可能少于总数。所有探测均不写数据、
 * 不返回联系方式等敏感列。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Mapper
public interface PlatformInspectionMapper {

    /**
     * 账号记录了当前家族，但已不是该家族成员
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT u.id AS refId, CONCAT(u.username, '(', COALESCE(u.nickname, '-'), ')') AS subject, "
            + "CONCAT('current_family_id=', u.current_family_id, ' 已无成员关系') AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM account u "
            + "LEFT JOIN family_member m ON m.user_id = u.id AND m.family_id = u.current_family_id "
            + "WHERE u.current_family_id IS NOT NULL AND m.id IS NULL "
            + "ORDER BY u.id LIMIT #{limit}")
    List<InspectionSampleDO> listAccountFamilyMismatch(@Param("limit") int limit);

    /**
     * 家族内没有任何族长
     *
     * @param ownerRole 族长角色编码
     * @param limit     样本条数上限
     * @return 异常样本
     */
    @Select("SELECT f.id AS refId, f.name AS subject, "
            + "CONCAT('成员 ', (SELECT COUNT(*) FROM family_member m WHERE m.family_id = f.id), ' 人，无族长') AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family f "
            + "WHERE NOT EXISTS (SELECT 1 FROM family_member m WHERE m.family_id = f.id AND m.role = #{ownerRole}) "
            + "ORDER BY f.id LIMIT #{limit}")
    List<InspectionSampleDO> listFamilyWithoutOwner(@Param("ownerRole") String ownerRole,
                                                    @Param("limit") int limit);

    /**
     * 成员记录指向不存在的家族
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT m.id AS refId, COALESCE(u.username, CONCAT('account#', m.user_id)) AS subject, "
            + "CONCAT('family_id=', m.family_id, ' 不存在') AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_member m "
            + "LEFT JOIN family f ON f.id = m.family_id "
            + "LEFT JOIN account u ON u.id = m.user_id "
            + "WHERE f.id IS NULL "
            + "ORDER BY m.id LIMIT #{limit}")
    List<InspectionSampleDO> listMemberOrphanFamily(@Param("limit") int limit);

    /**
     * 成员记录指向不存在的账号
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT m.id AS refId, COALESCE(f.name, CONCAT('family#', m.family_id)) AS subject, "
            + "CONCAT('user_id=', m.user_id, ' 不存在') AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_member m "
            + "LEFT JOIN family f ON f.id = m.family_id "
            + "LEFT JOIN account u ON u.id = m.user_id "
            + "WHERE u.id IS NULL "
            + "ORDER BY m.id LIMIT #{limit}")
    List<InspectionSampleDO> listMemberOrphanUser(@Param("limit") int limit);

    /**
     * 节点未归属家族或归属到不存在的家族
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT n.id AS refId, n.name AS subject, "
            + "CASE WHEN n.family_id IS NULL THEN 'node has no family' "
            + "ELSE CONCAT('family_id=', n.family_id, ' 不存在') END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_node n "
            + "LEFT JOIN family f ON f.id = n.family_id "
            + "WHERE n.family_id IS NULL OR f.id IS NULL "
            + "ORDER BY n.id LIMIT #{limit}")
    List<InspectionSampleDO> listNodeWithoutFamily(@Param("limit") int limit);

    /**
     * 关系两端存在已不存在的节点
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, CONCAT('relation#', r.id) AS subject, "
            + "CASE WHEN fn.id IS NULL AND tn.id IS NULL THEN '两端节点均不存在' "
            + "WHEN fn.id IS NULL THEN CONCAT('起点节点 ', r.from_node_id, ' 不存在') "
            + "ELSE CONCAT('终点节点 ', r.to_node_id, ' 不存在') END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "LEFT JOIN family_node fn ON fn.id = r.from_node_id "
            + "LEFT JOIN family_node tn ON tn.id = r.to_node_id "
            + "WHERE fn.id IS NULL OR tn.id IS NULL "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listRelationOrphanNode(@Param("limit") int limit);

    /**
     * 账号与节点的绑定关系失效
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT b.id AS refId, "
            + "CONCAT(COALESCE(u.username, CONCAT('account#', b.user_id)), ' -> ', "
            + "COALESCE(n.name, CONCAT('node#', b.node_id))) AS subject, "
            + "CASE WHEN n.id IS NULL THEN '绑定的节点不存在' "
            + "WHEN u.id IS NULL THEN '绑定的账号不存在' "
            + "WHEN n.family_id IS NULL THEN '绑定节点未归属家族' "
            + "ELSE CONCAT('节点家族 ', n.family_id, ' 与绑定家族 ', b.family_id, ' 不一致') END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_user_node b "
            + "LEFT JOIN family_node n ON n.id = b.node_id "
            + "LEFT JOIN account u ON u.id = b.user_id "
            + "WHERE n.id IS NULL OR u.id IS NULL OR n.family_id IS NULL OR n.family_id <> b.family_id "
            + "ORDER BY b.id LIMIT #{limit}")
    List<InspectionSampleDO> listNodeBindingMismatch(@Param("limit") int limit);

    /**
     * 关系两端为同一节点
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, COALESCE(n.name, CONCAT('node#', r.from_node_id)) AS subject, "
            + "CONCAT('relation_type=', r.relation_type, ' 两端都是节点 ', r.from_node_id) AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "LEFT JOIN family_node n ON n.id = r.from_node_id "
            + "WHERE r.from_node_id = r.to_node_id "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listRelationSelfLoop(@Param("limit") int limit);

    /**
     * 亲子关系的父辈世代不小于子辈世代
     *
     * @param parentChildType 亲子关系类型编码
     * @param limit           样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, CONCAT(p.name, ' -> ', c.name) AS subject, "
            + "CONCAT('父辈世代 ', p.generation, ' 不小于子辈世代 ', c.generation) AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "JOIN family_node p ON p.id = r.from_node_id "
            + "JOIN family_node c ON c.id = r.to_node_id "
            + "WHERE r.relation_type = #{parentChildType} AND p.generation IS NOT NULL "
            + "AND c.generation IS NOT NULL AND p.generation >= c.generation "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listGenerationInverted(@Param("parentChildType") int parentChildType,
                                                    @Param("limit") int limit);

    /**
     * 夫妻节点分属不同家族
     *
     * @param spouseType 夫妻关系类型编码
     * @param limit      样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, CONCAT(a.name, ' / ', b.name) AS subject, "
            + "CONCAT('夫妻节点分属家族 ', a.family_id, ' 与 ', b.family_id) AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "JOIN family_node a ON a.id = r.from_node_id "
            + "JOIN family_node b ON b.id = r.to_node_id "
            + "WHERE r.relation_type = #{spouseType} AND a.family_id IS NOT NULL AND b.family_id IS NOT NULL "
            + "AND a.family_id <> b.family_id "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listSpouseCrossFamily(@Param("spouseType") int spouseType,
                                                   @Param("limit") int limit);

    /**
     * 卒年不晚于生年
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT n.id AS refId, n.name AS subject, "
            + "CONCAT('生 ', n.birth_date, ' / 卒 ', n.death_date) AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_node n "
            + "WHERE n.birth_date IS NOT NULL AND n.death_date IS NOT NULL AND n.death_date <= n.birth_date "
            + "ORDER BY n.id LIMIT #{limit}")
    List<InspectionSampleDO> listDeathBeforeBirth(@Param("limit") int limit);

    /**
     * 系统代开账号的昵称仍是占位符
     *
     * @param autoAccountFlag 自动账号标记值
     * @param limit           样本条数上限
     * @return 异常样本
     */
    @Select("SELECT u.id AS refId, u.username AS subject, "
            + "CONCAT('昵称占位：', COALESCE(u.nickname, '-')) AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM account u "
            + "WHERE u.is_auto_account = #{autoAccountFlag} AND (u.nickname LIKE '%*%' OR u.nickname LIKE '%?%') "
            + "ORDER BY u.id LIMIT #{limit}")
    List<InspectionSampleDO> listAutoAccountPlaceholder(@Param("autoAccountFlag") boolean autoAccountFlag,
                                                        @Param("limit") int limit);
}
