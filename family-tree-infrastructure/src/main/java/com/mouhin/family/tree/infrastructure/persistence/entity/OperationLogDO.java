package com.mouhin.family.tree.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志数据对象
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Data
@TableName("operation_log")
public class OperationLogDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 操作用户名
     */
    private String username;

    /**
     * 操作类型（LOGIN / REGISTER / LOGOUT / NODE_CREATE / NODE_UPDATE / NODE_DELETE 等）
     */
    private String operationType;

    /**
     * 操作描述
     */
    private String operationDesc;

    /**
     * 操作对象类型（node / relation / family / user）
     */
    private String targetType;

    /**
     * 操作对象ID
     */
    private Long targetId;

    /**
     * 所属家族ID
     */
    private Long familyId;

    /**
     * 客户端IP
     */
    private String ipAddress;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;
}
