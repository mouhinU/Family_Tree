package com.mouhin.family.tree.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 族谱辈分（世代名称）数据对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
@TableName("family_generation")
public class FamilyGenerationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 世代（从1开始） */
    private Integer generation;

    /** 辈分名称（字辈） */
    private String name;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
