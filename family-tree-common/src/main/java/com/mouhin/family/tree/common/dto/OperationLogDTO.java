package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作日志展示对象
 *
 * @author Family-Tree
 * @date 2026-08-03
 */
@Getter
@Setter
public class OperationLogDTO {

    /**
     * 日志ID
     */
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
     * 操作类型
     */
    private String operationType;

    /**
     * 操作描述
     */
    private String operationDesc;

    /**
     * 操作对象类型
     */
    private String targetType;

    /**
     * 操作对象ID
     */
    private Long targetId;

    /**
     * 客户端IP
     */
    private String ipAddress;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;
}
