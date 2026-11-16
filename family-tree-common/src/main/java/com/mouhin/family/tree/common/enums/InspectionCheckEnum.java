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
