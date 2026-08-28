package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 关系修改历史数据对象
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
@TableName("family_relation_history")
public class RelationHistoryDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关系ID
     */
    private Long relationId;

    /**
     * 家族ID
     */
    private Long familyId;

    /**
     * 操作类型: CREATE/UPDATE/DELETE
     */
    private String operationType;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 修改前的数据(JSON格式)
     */
    private String beforeData;

    /**
     * 修改后的数据(JSON格式)
     */
    private String afterData;

    /**
     * 变更摘要
     */
    private String changeSummary;

    /**
     * 操作IP地址
     */
    private String ipAddress;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;

    /**
     * 版本号(从1开始递增)
     */
    private Integer versionNumber;
}
