package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 族谱节点数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Getter
@Setter
@TableName("family_node")
public class FamilyNodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long familyId;

    private String name;

    private Integer gender;

    private LocalDate birthDate;

    private LocalDate deathDate;

    private Integer generation;

    /**
     * 同胞排次（1=老大 2=老二 ...），null 表示未设置
     */
    private Integer birthOrder;

    private String colorLabel;

    private String avatar;

    private String remark;

    /**
     * 农历出生日期
     */
    private String lunarBirthDate;

    /**
     * 农历去世日期
     */
    private String lunarDeathDate;

    /**
     * 字
     */
    private String zi;

    /**
     * 号
     */
    private String hao;

    /**
     * 讳
     */
    private String hui;

    /**
     * 坟茔位置
     */
    private String graveLocation;

    /**
     * 配偶姓名（外嫁女婚配记录）
     */
    private String spouseName;

    /**
     * 婚配方家族（外嫁女婚配记录）
     */
    private String spouseOriginFamily;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
