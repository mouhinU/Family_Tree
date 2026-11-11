package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 族人详情视图对象：详情弹窗专用，独立于树形结构返回。
 * <p>
 * 仅对登录人在可见范围内的节点提供（非管理员只能查看其直系血缘链 + 这些节点直接配偶的详情）。
 * 字段命名与前端详情渲染保持一致，便于弹窗直接消费。
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
@Getter
@Setter
public class NodeDetailVO {

    private Long id;
    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    private Integer generation;

    /**
     * 同胞排次（1=老大 ...），null 表示未设置
     */
    private Integer birthOrder;

    /**
     * 颜色标注（前端据此计算卡片/详情配色）
     */
    private String colorLabel;

    /**
     * 备注
     */
    private String remark;

    private String lunarBirthDate;
    private String lunarDeathDate;
    private String zi;
    private String hao;
    private String hui;
    private String graveLocation;
    private String spouseName;
    private String spouseOriginFamily;

    /**
     * 在婚/续弦配偶（非血亲、未离异）
     */
    private List<SpouseRelation> spouses = new ArrayList<>();

    /**
     * 血亲配偶（本身在族谱中有原生分支）
     */
    private List<SpouseRelation> bloodSpouses = new ArrayList<>();

    /**
     * 离异/丧偶前配偶
     */
    private List<SpouseRelation> formerSpouses = new ArrayList<>();

    /**
     * 单条配偶关系明细
     *
     * @author Family-Tree
     * @date 2026-11-11
     */
    @Getter
    @Setter
    public static class SpouseRelation {

        /**
         * 配偶节点ID
         */
        private Long nodeId;

        /**
         * 配偶姓名
         */
        private String name;

        /**
         * 关系ID（用于婚姻设置管理）
         */
        private Long relationId;

        /**
         * 是否离异
         */
        private Boolean divorced;

        /**
         * 是否丧偶
         */
        private Boolean widowed;

        /**
         * 结婚日期
         */
        private String marriageDate;

        /**
         * 离异日期
         */
        private String divorceDate;

        /**
         * 血亲亲缘标签（血亲配偶时填充）
         */
        private String bloodRelationLabel;
    }
}
