package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
@TableName("family")
public class FamilyDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 家族名称 */
    private String name;

    /** 邀请码（唯一随机码） */
    private String inviteCode;

    /** 创建者（族长）用户ID */
    private Long creatorId;

    /** 堂号 */
    private String hallName;

    /** 籍贯 */
    private String ancestralHome;

    /** 辈分管理列数 */
    private Integer generationCols;

    /** 辈分管理行数 */
    private Integer generationRows;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
