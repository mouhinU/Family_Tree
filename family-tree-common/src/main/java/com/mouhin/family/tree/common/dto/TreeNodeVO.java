package com.mouhin.family.tree.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 族谱树形节点视图对象（含配偶和子节点）
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class TreeNodeVO {

    private Long id;
    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    private Integer generation;
    private String colorLabel;
    private String colorHex;
    private String avatar;
    private String remark;

    /** 同胞排次（1=老大 2=老二 ...），null 表示未设置 */
    private Integer birthOrder;

    /** 关系ID（配偶节点时填充，用于管理关系） */
    private Long relationId;

    /** 是否离异（配偶节点时有效） */
    private Boolean divorced;

    /** 是否丧偶（配偶节点时有效，表示配偶一方已去世） */
    private Boolean widowed;

    /** 结婚日期（配偶节点时有效） */
    private String marriageDate;

    /** 离异日期（配偶节点时有效） */
    private String divorceDate;

    /** 配偶列表（"嫁入/入赘"的卫星节点，随本节点渲染） */
    private List<TreeNodeVO> spouses = new ArrayList<>();

    /** 血亲配偶列表（如表兄妹结婚：配偶本身在族谱中有原生分支，
     *  不嵌入为卫星节点，仅保留引用，由前端绘制跨分支连线） */
    private List<TreeNodeVO> bloodSpouses = new ArrayList<>();

    /** 血亲亲缘标签（血亲配偶引用时填充）：按距最近共同祖先的世代数区分，
     *  2 代（共享祖父母）= 亲表兄妹，3 代（共享曾祖父母）= 堂表兄妹，更远 = 远房表亲 */
    private String bloodRelationLabel;

    /** 离异配偶引用列表（卫星配偶已改嫁/再婚至他处：其本人已作为卫星节点
     *  挂到当前配偶名下，本节点仅保留离异引用供详情弹窗展示，不再嵌入为卫星节点） */
    private List<TreeNodeVO> formerSpouses = new ArrayList<>();

    /** 子节点列表 */
    private List<TreeNodeVO> children = new ArrayList<>();
}
