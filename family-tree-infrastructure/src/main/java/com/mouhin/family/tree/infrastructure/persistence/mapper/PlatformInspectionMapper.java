package com.mouhin.family.tree.infrastructure.persistence.mapper;

import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeLunarRowDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.SettingRowDO;
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

    /**
     * 跨族亲族链端点失效或链上家族与节点实际归属不一致
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT l.id AS refId, "
            + "CONCAT(COALESCE(sn.name, CONCAT('node#', l.src_node_id)), ' ↔ ', "
            + "COALESCE(dn.name, CONCAT('node#', l.dst_node_id))) AS subject, "
            + "CASE WHEN sn.id IS NULL THEN CONCAT('源节点 ', l.src_node_id, ' 已删除') "
            + "WHEN dn.id IS NULL THEN CONCAT('目标节点 ', l.dst_node_id, ' 已删除') "
            + "WHEN sn.family_id <> l.src_family_id "
            + "THEN CONCAT('源节点实际家族 ', sn.family_id, ' 与链记录 ', l.src_family_id, ' 不一致') "
            + "ELSE CONCAT('目标节点实际家族 ', dn.family_id, ' 与链记录 ', l.dst_family_id, ' 不一致') END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_node_link l "
            + "LEFT JOIN family_node sn ON sn.id = l.src_node_id "
            + "LEFT JOIN family_node dn ON dn.id = l.dst_node_id "
            + "WHERE sn.id IS NULL OR dn.id IS NULL OR sn.family_id <> l.src_family_id "
            + "OR dn.family_id <> l.dst_family_id "
            + "ORDER BY l.id LIMIT #{limit}")
    List<InspectionSampleDO> listLinkOrphan(@Param("limit") int limit);

    /**
     * 同一对节点同一关系类型被不同创建人重复登记
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT d.min_id AS refId, "
            + "CONCAT(COALESCE(a.name, CONCAT('node#', d.from_node_id)), ' -> ', "
            + "COALESCE(b.name, CONCAT('node#', d.to_node_id))) AS subject, "
            + "CONCAT('关系类型 ', d.relation_type, ' 由 ', d.cnt, ' 个创建人重复登记') AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM (SELECT from_node_id, to_node_id, relation_type, COUNT(*) AS cnt, MIN(id) AS min_id "
            + "FROM family_relation GROUP BY from_node_id, to_node_id, relation_type "
            + "HAVING COUNT(*) > 1) d "
            + "LEFT JOIN family_node a ON a.id = d.from_node_id "
            + "LEFT JOIN family_node b ON b.id = d.to_node_id "
            + "ORDER BY d.min_id LIMIT #{limit}")
    List<InspectionSampleDO> listRelationDuplicateAcrossCreator(@Param("limit") int limit);

    /**
     * 亲子链存在环（沿父辈向上回溯能回到自身）
     *
     * @param parentChildType 亲子关系类型编码
     * @param maxDepth        递归深度上限，防御脏数据导致的无限展开
     * @param limit           样本条数上限
     * @return 异常样本
     */
    @Select("SELECT c.node_id AS refId, COALESCE(n.name, CONCAT('node#', c.node_id)) AS subject, "
            + "'亲子链成环：沿父辈回溯可回到自身' AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM (WITH RECURSIVE pc (start_node, node, depth) AS ( "
            + "SELECT to_node_id, from_node_id, 1 FROM family_relation WHERE relation_type = #{parentChildType} "
            + "UNION "
            + "SELECT pc.start_node, r.from_node_id, pc.depth + 1 FROM pc "
            + "JOIN family_relation r ON r.to_node_id = pc.node AND r.relation_type = #{parentChildType} "
            + "WHERE pc.depth < #{maxDepth}) "
            + "SELECT DISTINCT start_node AS node_id FROM pc WHERE start_node = node) c "
            + "LEFT JOIN family_node n ON n.id = c.node_id "
            + "ORDER BY c.node_id LIMIT #{limit}")
    List<InspectionSampleDO> listParentCycle(@Param("parentChildType") int parentChildType,
                                             @Param("maxDepth") int maxDepth,
                                             @Param("limit") int limit);

    /**
     * 关系记录与其两端节点的家族归属不一致
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, "
            + "CONCAT(COALESCE(a.name, CONCAT('node#', r.from_node_id)), ' -> ', "
            + "COALESCE(b.name, CONCAT('node#', r.to_node_id))) AS subject, "
            + "CASE WHEN a.family_id IS NOT NULL AND a.family_id <> r.family_id "
            + "THEN CONCAT('起点节点家族 ', a.family_id, ' 与关系家族 ', r.family_id, ' 不一致') "
            + "WHEN a.family_id IS NULL THEN '起点节点不存在或未归属家族' "
            + "WHEN b.family_id IS NOT NULL AND b.family_id <> r.family_id "
            + "THEN CONCAT('终点节点家族 ', b.family_id, ' 与关系家族 ', r.family_id, ' 不一致') "
            + "ELSE '终点节点不存在或未归属家族' END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "LEFT JOIN family_node a ON a.id = r.from_node_id "
            + "LEFT JOIN family_node b ON b.id = r.to_node_id "
            + "WHERE r.family_id IS NOT NULL AND (a.id IS NULL OR b.id IS NULL OR a.family_id IS NULL "
            + "OR b.family_id IS NULL OR a.family_id <> r.family_id OR b.family_id <> r.family_id) "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listRelationFamilyMismatch(@Param("limit") int limit);

    /**
     * 节点必填字段非法：姓名为空、性别越界或出生日期在未来
     *
     * @param minGender 性别枚举最小码
     * @param maxGender 性别枚举最大码
     * @param limit     样本条数上限
     * @return 异常样本
     */
    @Select("SELECT n.id AS refId, COALESCE(NULLIF(n.name, ''), CONCAT('node#', n.id)) AS subject, "
            + "CASE WHEN n.name IS NULL OR n.name = '' THEN '姓名为空' "
            + "WHEN n.gender IS NOT NULL AND (n.gender < #{minGender} OR n.gender > #{maxGender}) "
            + "THEN CONCAT('性别值 ', n.gender, ' 超出合法范围') "
            + "ELSE CONCAT('出生日期 ', n.birth_date, ' 晚于今天') END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_node n "
            + "WHERE n.name IS NULL OR n.name = '' "
            + "OR (n.gender IS NOT NULL AND (n.gender < #{minGender} OR n.gender > #{maxGender})) "
            + "OR (n.birth_date IS NOT NULL AND n.birth_date > CURRENT_DATE) "
            + "ORDER BY n.id LIMIT #{limit}")
    List<InspectionSampleDO> listNodeFieldInvalid(@Param("minGender") int minGender,
                                                  @Param("maxGender") int maxGender,
                                                  @Param("limit") int limit);

    /**
     * 家族规模超过生效配额：节点数超上限（含家族级覆盖）或成员数超平台上限
     *
     * @param nodeCountKey     节点数上限的家族覆盖配置键
     * @param maxNodeCount     平台生效的节点数上限
     * @param maxFamilyMembers 平台生效的成员数上限
     * @param limit            样本条数上限
     * @return 异常样本
     */
    @Select("SELECT t.family_id AS refId, COALESCE(f.name, CONCAT('family#', t.family_id)) AS subject, "
            + "t.detail AS detail, COUNT(*) OVER () AS total "
            + "FROM (SELECT nc.family_id AS family_id, "
            + "CONCAT('节点数 ', nc.cnt, ' 超过上限 ', nc.quota) AS detail "
            + "FROM (SELECT n.family_id AS family_id, COUNT(*) AS cnt, "
            + "COALESCE(CAST(MAX(fs.setting_value) AS DECIMAL(10, 0)), CAST(#{maxNodeCount} AS DECIMAL(10, 0))) AS quota "
            + "FROM family_node n "
            + "LEFT JOIN family_setting fs ON fs.family_id = n.family_id AND fs.setting_key = #{nodeCountKey} "
            + "GROUP BY n.family_id) nc "
            + "WHERE nc.cnt > nc.quota "
            + "UNION ALL "
            + "SELECT mc.family_id AS family_id, "
            + "CONCAT('成员数 ', mc.cnt, ' 超过上限 ', #{maxFamilyMembers}) AS detail "
            + "FROM (SELECT m.family_id AS family_id, COUNT(*) AS cnt FROM family_member m "
            + "GROUP BY m.family_id) mc "
            + "WHERE mc.cnt > #{maxFamilyMembers}) t "
            + "LEFT JOIN family f ON f.id = t.family_id "
            + "ORDER BY t.family_id LIMIT #{limit}")
    List<InspectionSampleDO> listQuotaExceeded(@Param("nodeCountKey") String nodeCountKey,
                                               @Param("maxNodeCount") int maxNodeCount,
                                               @Param("maxFamilyMembers") int maxFamilyMembers,
                                               @Param("limit") int limit);

    /**
     * 系统代开账号没有任何节点绑定
     *
     * @param autoAccountFlag 自动账号标记值
     * @param limit           样本条数上限
     * @return 异常样本
     */
    @Select("SELECT u.id AS refId, CONCAT(u.username, '(', COALESCE(u.nickname, '-'), ')') AS subject, "
            + "'自动账号无任何节点绑定' AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM account u "
            + "LEFT JOIN family_user_node b ON b.user_id = u.id "
            + "WHERE u.is_auto_account = #{autoAccountFlag} AND b.id IS NULL "
            + "ORDER BY u.id LIMIT #{limit}")
    List<InspectionSampleDO> listAutoAccountOrphan(@Param("autoAccountFlag") boolean autoAccountFlag,
                                                   @Param("limit") int limit);

    /**
     * 夫妻关系的离异/丧偶标志与日期、卒年互相矛盾
     *
     * @param spouseType 夫妻关系类型编码
     * @param trueFlag   布尔真值（跨库比较 is_divorced / is_widowed 用）
     * @param limit      样本条数上限
     * @return 异常样本
     */
    @Select("SELECT r.id AS refId, "
            + "CONCAT(COALESCE(a.name, CONCAT('node#', r.from_node_id)), ' / ', "
            + "COALESCE(b.name, CONCAT('node#', r.to_node_id))) AS subject, "
            + "CASE WHEN r.divorce_date IS NULL THEN '标记已离异但无离婚日期' "
            + "WHEN a.death_date IS NULL AND b.death_date IS NULL "
            + "THEN '标记丧偶但双方节点均无卒年' "
            + "ELSE '有离婚日期但未标记离异' END AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM family_relation r "
            + "LEFT JOIN family_node a ON a.id = r.from_node_id "
            + "LEFT JOIN family_node b ON b.id = r.to_node_id "
            + "WHERE r.relation_type = #{spouseType} AND ("
            + "(r.is_divorced = #{trueFlag} AND r.divorce_date IS NULL) "
            + "OR ((r.is_divorced IS NULL OR r.is_divorced <> #{trueFlag}) AND r.divorce_date IS NOT NULL) "
            + "OR (r.is_widowed = #{trueFlag} AND a.death_date IS NULL AND b.death_date IS NULL)) "
            + "ORDER BY r.id LIMIT #{limit}")
    List<InspectionSampleDO> listRelationFlagDateMismatch(@Param("spouseType") int spouseType,
                                                           @Param("trueFlag") boolean trueFlag,
                                                           @Param("limit") int limit);

    /**
     * 节点/家族附属数据（照片、私聊、论坛回帖）指向已删除的账号、家族或主题
     *
     * @param limit 样本条数上限
     * @return 异常样本
     */
    @Select("SELECT t.biz_id AS refId, t.subject AS subject, t.detail AS detail, "
            + "COUNT(*) OVER () AS total "
            + "FROM (SELECT p.id AS biz_id, COALESCE(NULLIF(p.title, ''), CONCAT('photo#', p.id)) AS subject, "
            + "CASE WHEN u.id IS NULL THEN CONCAT('上传者账号 ', p.user_id, ' 不存在') "
            + "ELSE CONCAT('所属家族 ', p.family_id, ' 不存在') END AS detail "
            + "FROM family_photo p "
            + "LEFT JOIN account u ON u.id = p.user_id "
            + "LEFT JOIN family f ON f.id = p.family_id "
            + "WHERE (p.user_id IS NOT NULL AND u.id IS NULL) OR (p.family_id IS NOT NULL AND f.id IS NULL) "
            + "UNION ALL "
            + "SELECT pm.id, CONCAT(COALESCE(pm.sender_name, CONCAT('account#', pm.sender_id)), ' -> ', "
            + "COALESCE(ra.username, CONCAT('account#', pm.receiver_id))), "
            + "CASE WHEN sa.id IS NULL THEN CONCAT('发送者账号 ', pm.sender_id, ' 不存在') "
            + "ELSE CONCAT('接收者账号 ', pm.receiver_id, ' 不存在') END "
            + "FROM family_private_message pm "
            + "LEFT JOIN account sa ON sa.id = pm.sender_id "
            + "LEFT JOIN account ra ON ra.id = pm.receiver_id "
            + "WHERE (pm.sender_id IS NOT NULL AND sa.id IS NULL) OR (pm.receiver_id IS NOT NULL AND ra.id IS NULL) "
            + "UNION ALL "
            + "SELECT fr.id, COALESCE(NULLIF(fr.username, ''), CONCAT('account#', fr.user_id)), "
            + "CASE WHEN ft.id IS NULL THEN CONCAT('回帖指向已删除主题 ', fr.topic_id) "
            + "ELSE CONCAT('回帖作者账号 ', fr.user_id, ' 不存在') END "
            + "FROM family_forum_reply fr "
            + "LEFT JOIN family_forum_topic ft ON ft.id = fr.topic_id "
            + "LEFT JOIN account ua ON ua.id = fr.user_id "
            + "WHERE (fr.topic_id IS NOT NULL AND ft.id IS NULL) OR (fr.user_id IS NOT NULL AND ua.id IS NULL)) t "
            + "ORDER BY t.biz_id LIMIT #{limit}")
    List<InspectionSampleDO> listAttachedOrphan(@Param("limit") int limit);

    /**
     * 取登记了阳历与农历双生日的节点行，供 Java 侧按阳历重算农历后比对
     *
     * @return 节点生日行（表小，全量返回）
     */
    @Select("SELECT n.id AS id, n.name AS name, n.birth_date AS birthDate, "
            + "n.lunar_birth_date AS lunarBirthDate "
            + "FROM family_node n "
            + "WHERE n.birth_date IS NOT NULL AND n.lunar_birth_date IS NOT NULL AND n.lunar_birth_date <> '' "
            + "ORDER BY n.id")
    List<NodeLunarRowDO> listBirthDateNodes();

    /**
     * 全量取平台与家族两级配置行，供 Java 侧按配置键白名单过滤未知键
     *
     * @return 配置行（两表极小，全量返回）
     */
    @Select("SELECT 'FAMILY' AS scope, s.id AS id, s.family_id AS familyId, "
            + "s.setting_key AS settingKey, s.setting_value AS settingValue "
            + "FROM family_setting s "
            + "UNION ALL "
            + "SELECT 'PLATFORM' AS scope, p.id AS id, NULL AS familyId, "
            + "p.setting_key AS settingKey, p.setting_value AS settingValue "
            + "FROM platform_setting p "
            + "ORDER BY scope, id")
    List<SettingRowDO> listSettingRows();
}
