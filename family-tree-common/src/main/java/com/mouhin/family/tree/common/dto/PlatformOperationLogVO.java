package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 平台管理页操作日志检索行视图对象
 *
 * <p>跨家族展示，因此额外携带家族名称；日志本身记录的是当时的操作者名称，
 * 账号改名或注销后仍按留痕值展示，不做回溯。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Getter
@Setter
public class PlatformOperationLogVO {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 操作用户ID，系统触发或匿名操作时为 null
     */
    private Long userId;

    /**
     * 操作时的用户名留痕
     */
    private String username;

    /**
     * 操作类型编码
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
     * 所属家族ID，登录等家族无关操作为 null
     */
    private Long familyId;

    /**
     * 所属家族名称，家族已删除或家族无关时为 null
     */
    private String familyName;

    /**
     * 客户端IP
     */
    private String ipAddress;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;

    @Override
    public String toString() {
        // 故意不输出 ipAddress：日志行会进入应用日志，来源IP无需二次留痕
        return "PlatformOperationLogVO{"
                + "id=" + id
                + ", userId=" + userId
                + ", username='" + username + '\''
                + ", operationType='" + operationType + '\''
                + ", operationDesc='" + operationDesc + '\''
                + ", targetType='" + targetType + '\''
                + ", targetId=" + targetId
                + ", familyId=" + familyId
                + ", familyName='" + familyName + '\''
                + '}';
    }
}
