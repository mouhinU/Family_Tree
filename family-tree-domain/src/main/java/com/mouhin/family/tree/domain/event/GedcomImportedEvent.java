package com.mouhin.family.tree.domain.event;

import java.time.LocalDateTime;

/**
 * GEDCOM 导入事件。
 * <p>
 * 当通过 GEDCOM 文件批量导入族谱数据时发布。
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
public record GedcomImportedEvent(Long familyId, int nodeCount, int relationCount,
                                  boolean overwrite, Long userId, String username,
                                  String ipAddress, LocalDateTime occurredOn) implements DomainEvent {

    /**
     * 创建 GEDCOM 导入事件实例
     *
     * @param familyId      家族ID
     * @param nodeCount     导入节点数量
     * @param relationCount 导入关系数量
     * @param overwrite     是否覆盖模式
     * @param userId        操作用户ID
     * @param username      操作用户名
     * @param ipAddress     客户端IP
     * @return 事件实例
     */
    public static GedcomImportedEvent of(Long familyId, int nodeCount, int relationCount,
                                         boolean overwrite, Long userId, String username,
                                         String ipAddress) {
        return new GedcomImportedEvent(familyId, nodeCount, relationCount, overwrite, userId,
                username, ipAddress, LocalDateTime.now());
    }
}
