package com.mouhin.family.tree.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 祭奠记录数据对象
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Data
@TableName("family_offering")
public class FamilyOfferingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上香/烧纸的用户ID */
    private Long userId;

    /** 所属家族ID */
    private Long familyId;

    /** 受祭的已故节点ID */
    private Long nodeId;

    /** 祭奠类型：1-香烛 2-烧纸 */
    private Integer offeringType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
