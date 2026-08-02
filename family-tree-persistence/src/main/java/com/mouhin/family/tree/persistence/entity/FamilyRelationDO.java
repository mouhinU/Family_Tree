package com.mouhin.family.tree.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 族谱关系数据对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
@TableName("family_relation")
public class FamilyRelationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关系起点节点（亲子关系中为父节点，夫妻关系中为男方） */
    private Long fromNodeId;

    /** 关系终点节点（亲子关系中为子节点，夫妻关系中为女方） */
    private Long toNodeId;

    /** 关系类型：1-亲子 2-夫妻 */
    private Integer relationType;

    private LocalDate marriageDate;

    private LocalDate divorceDate;

    /** 是否离异（独立于离异日期，日期可为空） */
    @TableField("is_divorced")
    private Boolean divorced;

    /** 是否丧偶（配偶一方去世，独立于离异状态） */
    @TableField("is_widowed")
    private Boolean widowed;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
