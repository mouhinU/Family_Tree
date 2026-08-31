package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 家族纪念日数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_anniversary")
public class FamilyAnniversaryDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 关联族谱节点ID
     */
    private Long nodeId;

    /**
     * 创建用户ID
     */
    private Long userId;

    /**
     * 纪念日标题
     */
    private String title;

    /**
     * 分类编码
     */
    private String category;

    /**
     * 纪念日日期
     */
    private LocalDate anniversaryDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
