package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 跨族节点链接数据对象
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
@TableName("family_node_link")
public class FamilyNodeLinkDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 源家族ID（较小节点ID端所在家族）
     */
    private Long srcFamilyId;

    /**
     * 源族谱节点ID（规范化为较小节点ID）
     */
    private Long srcNodeId;

    /**
     * 目标家族ID
     */
    private Long dstFamilyId;

    /**
     * 目标族谱节点ID（规范化为较大节点ID）
     */
    private Long dstNodeId;

    /**
     * 链接类型：1血缘同人 2姻亲指认
     */
    private Integer linkType;

    /**
     * 成链来源：1账号自动 2人工指认 3候选确认
     */
    private Integer source;

    /**
     * 确认人用户ID（账号自动来源为空）
     */
    private Long confirmedBy;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
