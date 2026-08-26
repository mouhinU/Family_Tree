package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 家族留言数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
@TableName("family_message")
public class FamilyMessageDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 家族ID */
    private Long familyId;

    /** 留言用户ID */
    private Long userId;

    /** 留言用户名 */
    private String username;

    /** 留言内容 */
    private String content;

    /** 点赞数 */
    private Long likeCount;

    /** 留言分类 */
    private String category;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
