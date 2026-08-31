package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 照片人物标记数据对象
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Getter
@Setter
@TableName("family_photo_tag")
public class PhotoTagDO {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 照片ID
     */
    private Long photoId;

    /**
     * 族谱节点ID
     */
    private Long nodeId;

    /**
     * 节点姓名
     */
    private String nodeName;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
