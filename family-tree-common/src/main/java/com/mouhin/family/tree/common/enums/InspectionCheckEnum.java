package com.mouhin.family.tree.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;

/**
 * 数据巡检项：每项对应一条只读异常探测语句。
 *
 * <p>巡检项只增不改，新增时需同步在
 * {@code PlatformInspectionRepositoryImpl} 中补上对应的探测分支。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@AllArgsConstructor
public enum InspectionCheckEnum {

    ACCOUNT_FAMILY_MISMATCH("ACCOUNT_FAMILY_MISMATCH", "账号当前家族失效",
            "账号记录了当前家族，但该账号已不是此家族成员，登录后会进入无权限的家族",
            InspectionSeverityEnum.HIGH),

    FAMILY_WITHOUT_OWNER("FAMILY_WITHOUT_OWNER", "家族无族长",
            "家族内没有任何 OWNER 角色成员，无人可以管理家族与族谱",
            InspectionSeverityEnum.HIGH),

    MEMBER_ORPHAN_FAMILY("MEMBER_ORPHAN_FAMILY", "成员记录指向不存在的家族",
            "家族成员行的家族ID在家族表中不存在，成员列表与统计会带上脏数据",
            InspectionSeverityEnum.HIGH),

    MEMBER_ORPHAN_USER("MEMBER_ORPHAN_USER", "成员记录指向不存在的账号",
            "家族成员行的账号ID在用户表中不存在，成员列表会出现空白成员",
            InspectionSeverityEnum.HIGH),

    NODE_WITHOUT_FAMILY("NODE_WITHOUT_FAMILY", "节点缺少有效家族归属",
            "族谱节点的家族ID为空或指向不存在的家族，节点在任何家族视图中都不可见",
            InspectionSeverityEnum.HIGH),

    RELATION_ORPHAN_NODE("RELATION_ORPHAN_NODE", "关系指向不存在的节点",
            "亲属关系的两端有一端已不在节点表中，树形构建与关系路径会中断",
            InspectionSeverityEnum.HIGH),

    NODE_BINDING_MISMATCH("NODE_BINDING_MISMATCH", "账号节点绑定失效",
            "账号与族谱节点的绑定关系缺失或与节点所属家族不一致，本人标记会指向错误节点",
            InspectionSeverityEnum.HIGH),

    RELATION_SELF_LOOP("RELATION_SELF_LOOP", "关系两端为同一节点",
            "亲属关系的起点与终点是同一节点，属于写入校验遗漏",
            InspectionSeverityEnum.MEDIUM),

    GENERATION_INVERTED("GENERATION_INVERTED", "亲子世代倒挂",
            "父辈节点的世代号不小于子辈节点，排次与辈分展示会错乱",
            InspectionSeverityEnum.MEDIUM),

    SPOUSE_CROSS_FAMILY("SPOUSE_CROSS_FAMILY", "夫妻节点分属不同家族",
            "夫妻关系两端节点归属不同家族，单家族视图内会出现断开的配偶",
            InspectionSeverityEnum.MEDIUM),

    DEATH_BEFORE_BIRTH("DEATH_BEFORE_BIRTH", "卒年早于或等于生年",
            "节点的卒年不晚于生年，多为历史数据录入错误",
            InspectionSeverityEnum.MEDIUM),

    AUTO_ACCOUNT_PLACEHOLDER("AUTO_ACCOUNT_PLACEHOLDER", "代开账号昵称为占位符",
            "系统代开账号的昵称由 * 或 ? 占位，源于早期导入的乱码姓名",
            InspectionSeverityEnum.LOW),

    LINK_ORPHAN("LINK_ORPHAN", "跨族亲族链失效",
            "亲族链端点指向已删除节点，或链上记录的家族与节点实际归属不一致，详情亲族区块会指向错误家族",
            InspectionSeverityEnum.HIGH),

    RELATION_DUPLICATE_ACROSS_CREATOR("RELATION_DUPLICATE_ACROSS_CREATOR", "同对节点重复关系边",
            "同一对节点间存在多条同类型关系；关系表唯一键含创建人，不同成员可重复登记同一条边，树与关系路径会出现平行双线",
            InspectionSeverityEnum.HIGH),

    PARENT_CYCLE("PARENT_CYCLE", "亲子关系成环",
            "沿亲子边可从某节点回到自身（祖先链回指），树构建与世代深度计算会陷入循环",
            InspectionSeverityEnum.HIGH),

    RELATION_FAMILY_MISMATCH("RELATION_FAMILY_MISMATCH", "关系家族与节点家族不一致",
            "关系行冗余记录的家族ID与两端节点的实际家族不一致，多为节点迁移或数据订正后的漂移",
            InspectionSeverityEnum.HIGH),

    NODE_FIELD_INVALID("NODE_FIELD_INVALID", "节点字段值非法",
            "节点姓名为空、性别不在枚举值域或出生日期晚于今天，多为界面年份误输或历史脏数据",
            InspectionSeverityEnum.HIGH),

    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "家族规模超出配额",
            "家族节点数或成员数已超出当前生效的上限；收紧配额只拦新增不清存量，需巡检暴露存量超限家族",
            InspectionSeverityEnum.HIGH),

    AUTO_ACCOUNT_ORPHAN("AUTO_ACCOUNT_ORPHAN", "代开账号无节点绑定",
            "系统代开账号在绑定表中没有任何记录，本人无法通过该账号定位到族谱节点",
            InspectionSeverityEnum.MEDIUM),

    RELATION_FLAG_DATE_MISMATCH("RELATION_FLAG_DATE_MISMATCH", "婚姻标志与日期矛盾",
            "离异标志与离婚日期互相矛盾，或标记丧偶但配偶节点没有卒年，婚姻状态展示会自相矛盾",
            InspectionSeverityEnum.MEDIUM),

    ATTACHED_ORPHAN("ATTACHED_ORPHAN", "附属记录外键孤儿",
            "照片、私聊、论坛回复指向已删除的账号、家族或主题，级联清理上线前的历史脏行",
            InspectionSeverityEnum.MEDIUM),

    SETTING_UNKNOWN_KEY("SETTING_UNKNOWN_KEY", "配置表存在未登记键",
            "平台或家族配置表里出现枚举白名单之外的键，写入侧有白名单校验，主要防手工订正数据库引入的漂移",
            InspectionSeverityEnum.LOW),

    LUNAR_SOLAR_MISMATCH("LUNAR_SOLAR_MISMATCH", "农历生日与阳历不符",
            "节点登记了阳历出生日期，但农历串与按阳历重算的结果不一致，农历优先展示会显示错误日期",
            InspectionSeverityEnum.LOW);

    /**
     * 巡检项编码，同时作为接口返回的标识
     */
    private final String code;

    /**
     * 巡检项标题
     */
    private final String title;

    /**
     * 巡检项含义说明
     */
    private final String description;

    /**
     * 巡检项严重级别
     */
    private final InspectionSeverityEnum severity;

    /**
     * 根据编码查找巡检项
     *
     * @param code 巡检项编码
     * @return 命中的巡检项，未命中返回空
     */
    public static Optional<InspectionCheckEnum> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        for (InspectionCheckEnum check : values()) {
            if (check.code.equals(code)) {
                return Optional.of(check);
            }
        }
        return Optional.empty();
    }
}
