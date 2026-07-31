package com.mouhin.family.tree.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 族谱节点数据对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
@TableName("family_node")
public class FamilyNodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private Integer gender;

    private LocalDate birthDate;

    private LocalDate deathDate;

    private Integer generation;

    /** 同胞排次（1=老大 2=老二 ...），null 表示未设置 */
    private Integer birthOrder;

    private String colorLabel;

    private String avatar;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
