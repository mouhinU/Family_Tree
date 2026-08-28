package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 家族快照数据对象
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
@TableName("family_snapshot")
public class FamilySnapshotDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 快照名称
     */
    private String snapshotName;

    /**
     * 快照描述
     */
    private String description;

    /**
     * 创建人ID
     */
    private Long creatorId;

    /**
     * 创建人姓名
     */
    private String creatorName;

    /**
     * 节点数量
     */
    private Integer nodeCount;

    /**
     * 关系数量
     */
    private Integer relationCount;

    /**
     * 快照数据(JSON格式,包含所有节点和关系)
     */
    private String snapshotData;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
