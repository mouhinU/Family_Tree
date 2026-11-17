package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.entity.FamilySnapshot;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.entity.RelationHistory;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.event.NodeUpdatedEvent;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.HistoryRepository;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import com.mouhin.family.tree.domain.service.VersionControlDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 版本控制应用服务
 * <p>
 * 提供节点和关系的历史查询、版本对比、快照管理等功能
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Service
public class VersionControlApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(VersionControlApplicationService.class);

    /**
     * 操作日志类型：节点版本回滚
     */
    private static final String OP_VERSION_ROLLBACK = "VERSION_ROLLBACK";
    /**
     * 操作日志对象类型：族谱节点
     */
    private static final String TARGET_TYPE_NODE = "node";

    private final HistoryRepository historyRepository;
    private final SnapshotRepository snapshotRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final VersionControlDomainService versionControlDomainService;
    private final FamilyNodeApplicationService familyNodeApplicationService;
    private final FamilyUserNodeApplicationService familyUserNodeApplicationService;
    private final ApplicationEventPublisher eventPublisher;

    public VersionControlApplicationService(HistoryRepository historyRepository,
                                            SnapshotRepository snapshotRepository,
                                            FamilyNodeRepository familyNodeRepository,
                                            FamilyRelationRepository familyRelationRepository,
                                            VersionControlDomainService versionControlDomainService,
                                            FamilyNodeApplicationService familyNodeApplicationService,
                                            FamilyUserNodeApplicationService familyUserNodeApplicationService,
                                            ApplicationEventPublisher eventPublisher) {
        this.historyRepository = historyRepository;
        this.snapshotRepository = snapshotRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.versionControlDomainService = versionControlDomainService;
        this.familyNodeApplicationService = familyNodeApplicationService;
        this.familyUserNodeApplicationService = familyUserNodeApplicationService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询节点修改历史（分页）
     *
     * @param nodeId   节点ID
     * @param familyId 家族ID
     * @param page     页码（从1开始）
     * @param size     每页大小
     * @return 历史记录列表
     */
    public List<NodeHistory> getNodeHistory(Long nodeId, Long familyId, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        return historyRepository.findNodeHistoryByNodeId(nodeId, familyId, offset, size);
    }

    /**
     * 统计节点历史记录数
     *
     * @param nodeId   节点ID
     * @param familyId 家族ID
     * @return 记录数
     */
    public long countNodeHistory(Long nodeId, Long familyId) {
        return historyRepository.countNodeHistoryByNodeId(nodeId, familyId);
    }

    /**
     * 查询关系修改历史（分页）
     *
     * @param relationId 关系ID
     * @param familyId   家族ID
     * @param page       页码（从1开始）
     * @param size       每页大小
     * @return 历史记录列表
     */
    public List<RelationHistory> getRelationHistory(Long relationId, Long familyId, int page, int size) {
        if (page < 1) {
            page = 1;
        }
        if (size < 1 || size > 100) {
            size = 20;
        }
        int offset = (page - 1) * size;
        return historyRepository.findRelationHistoryByRelationId(relationId, familyId, offset, size);
    }

    /**
     * 统计关系历史记录数
     *
     * @param relationId 关系ID
     * @param familyId   家族ID
     * @return 记录数
     */
    public long countRelationHistory(Long relationId, Long familyId) {
        return historyRepository.countRelationHistoryByRelationId(relationId, familyId);
    }

    /**
     * 对比节点两个版本的差异
     *
     * @param nodeId        节点ID
     * @param versionNumber1 版本号1
     * @param versionNumber2 版本号2
     * @return 差异Map（字段名 -> [旧值, 新值]）
     */
    public Map<String, Object[]> compareNodeVersions(Long nodeId, Integer versionNumber1, Integer versionNumber2,
                                                     Long familyId) {
        NodeHistory history1 = historyRepository.findNodeHistoryByVersion(nodeId, versionNumber1);
        NodeHistory history2 = historyRepository.findNodeHistoryByVersion(nodeId, versionNumber2);

        if (history1 == null) {
            throw new BusinessException("版本" + versionNumber1 + "不存在");
        }
        if (history2 == null) {
            throw new BusinessException("版本" + versionNumber2 + "不存在");
        }
        if (!Objects.equals(history1.getFamilyId(), familyId) || !Objects.equals(history2.getFamilyId(), familyId)) {
            throw new BusinessException("无权访问该版本数据");
        }

        String beforeData = history1.getAfterData() != null ? history1.getAfterData() : history1.getBeforeData();
        String afterData = history2.getAfterData() != null ? history2.getAfterData() : history2.getBeforeData();

        if (beforeData == null || afterData == null) {
            throw new BusinessException("版本数据不完整，无法对比");
        }

        return versionControlDomainService.compareVersions(beforeData, afterData);
    }

    /**
     * 对比关系两个版本的差异
     *
     * @param relationId    关系ID
     * @param versionNumber1 版本号1
     * @param versionNumber2 版本号2
     * @return 差异Map（字段名 -> [旧值, 新值]）
     */
    public Map<String, Object[]> compareRelationVersions(Long relationId, Integer versionNumber1, Integer versionNumber2,
                                                          Long familyId) {
        RelationHistory history1 = historyRepository.findRelationHistoryByVersion(relationId, versionNumber1);
        RelationHistory history2 = historyRepository.findRelationHistoryByVersion(relationId, versionNumber2);

        if (history1 == null) {
            throw new BusinessException("版本" + versionNumber1 + "不存在");
        }
        if (history2 == null) {
            throw new BusinessException("版本" + versionNumber2 + "不存在");
        }
        if (!Objects.equals(history1.getFamilyId(), familyId) || !Objects.equals(history2.getFamilyId(), familyId)) {
            throw new BusinessException("无权访问该版本数据");
        }

        String beforeData = history1.getAfterData() != null ? history1.getAfterData() : history1.getBeforeData();
        String afterData = history2.getAfterData() != null ? history2.getAfterData() : history2.getBeforeData();

        if (beforeData == null || afterData == null) {
            throw new BusinessException("版本数据不完整，无法对比");
        }

        return versionControlDomainService.compareVersions(beforeData, afterData);
    }

    /**
     * 回滚节点到指定版本
     * <p>
     * 以目标版本的人物内容覆盖当前节点并落库，同时按新版本写入一条 ROLLBACK 历史，
     * 使回滚本身也留在历史时间线上、可再次回滚。主键、家族归属与账号绑定保持不变；
     * 世代变化沿用节点编辑的配偶/后代联动，改名沿用绑定账号昵称同步。
     *
     * @param nodeId        节点ID
     * @param versionNumber 目标版本号
     * @param familyId      家族ID
     * @param userId        操作用户ID
     * @param username      操作用户名
     * @param ipAddress     客户端IP
     * @return 目标版本的节点数据（JSON字符串）
     */
    @Transactional(rollbackFor = Exception.class)
    public String rollbackNodeToVersion(Long nodeId, Integer versionNumber, Long familyId,
                                        Long userId, String username, String ipAddress) {
        NodeHistory history = historyRepository.findNodeHistoryByVersion(nodeId, versionNumber);
        if (history == null) {
            throw new BusinessException("版本" + versionNumber + "不存在");
        }
        if (!Objects.equals(history.getFamilyId(), familyId)) {
            throw new BusinessException("无权回滚该节点版本");
        }
        if (history.getAfterData() == null) {
            // 删除版本只留有删除前快照，回滚等价于重建节点，不在版本回滚的能力范围内
            throw new BusinessException("该版本为删除记录，无法回滚");
        }

        FamilyNode versionNode = versionControlDomainService.parseNodeVersion(history.getAfterData());
        FamilyNode existing = familyNodeRepository.findById(nodeId);
        if (existing == null || !Objects.equals(existing.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在，无法回滚");
        }

        FamilyNode beforeNode = existing.copy();
        existing.restoreContentFrom(versionNode);
        existing.validateForUpdate();
        existing.setUpdateTime(LocalDateTime.now());
        familyNodeRepository.update(existing);

        if (!Objects.equals(beforeNode.getGeneration(), existing.getGeneration())) {
            familyNodeApplicationService.syncGenerationCascade(familyId, nodeId, existing.getGeneration());
        }

        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
        eventPublisher.publishEvent(NodeUpdatedEvent.of(familyId, nodeId, existing.getName(),
                userId, username, ipAddress));
        versionControlDomainService.recordNodeRollback(beforeNode, existing, versionNumber,
                userId, username, ipAddress);

        if (!Objects.equals(beforeNode.getName(), existing.getName())) {
            familyUserNodeApplicationService.syncNicknameFromNode(familyId, nodeId, existing.getName());
        }

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, OP_VERSION_ROLLBACK,
                "回滚节点到版本" + versionNumber, TARGET_TYPE_NODE, nodeId, familyId, ipAddress));
        return history.getAfterData();
    }

    /**
     * 创建家族快照
     *
     * @param familyId     家族ID
     * @param snapshotName 快照名称
     * @param description  快照描述
     * @param creatorId    创建人ID
     * @param creatorName  创建人姓名
     * @param ipAddress    客户端IP
     * @return 创建的快照
     */
    @Transactional(rollbackFor = Exception.class)
    public FamilySnapshot createSnapshot(Long familyId, String snapshotName, String description,
                                          Long creatorId, String creatorName, String ipAddress) {
        // 获取家族所有节点和关系
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> relations = familyRelationRepository.findByFamilyId(familyId);

        FamilySnapshot snapshot = versionControlDomainService.createSnapshot(
                familyId,
                snapshotName,
                description,
                creatorId,
                creatorName,
                nodes,
                relations
        );

        eventPublisher.publishEvent(OperationPerformedEvent.of(creatorId, creatorName, "VERSION_SNAPSHOT",
                "创建家族快照: " + snapshotName, "snapshot", snapshot.getId(), familyId, ipAddress));
        return snapshot;
    }

    /**
     * 列出家族的所有快照
     *
     * @param familyId 家族ID
     * @return 快照列表
     */
    public List<FamilySnapshot> listSnapshots(Long familyId) {
        return snapshotRepository.findByFamilyId(familyId);
    }

    /**
     * 根据ID查询快照详情（校验归属）
     *
     * @param snapshotId 快照ID
     * @param familyId   当前家族ID
     * @return 快照对象
     */
    public FamilySnapshot getSnapshot(Long snapshotId, Long familyId) {
        FamilySnapshot snapshot = snapshotRepository.findById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException("快照不存在");
        }
        if (!Objects.equals(snapshot.getFamilyId(), familyId)) {
            throw new BusinessException("无权访问该快照");
        }
        return snapshot;
    }

    /**
     * 删除快照（校验归属后删除）
     *
     * @param snapshotId 快照ID
     * @param familyId   当前家族ID
     * @param userId     操作用户ID
     * @param username   操作用户名
     * @param ipAddress  客户端IP
     */
    public void deleteSnapshot(Long snapshotId, Long familyId, Long userId,
                               String username, String ipAddress) {
        FamilySnapshot snapshot = snapshotRepository.findById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException("快照不存在");
        }
        if (!Objects.equals(snapshot.getFamilyId(), familyId)) {
            throw new BusinessException("无权删除该快照");
        }
        snapshotRepository.removeById(snapshotId);

        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "VERSION_SNAPSHOT_DELETE",
                "删除家族快照: " + snapshot.getSnapshotName(), "snapshot", snapshotId, familyId, ipAddress));
    }

    /**
     * 从快照恢复数据
     * <p>
     * 注意：此方法仅返回快照数据，实际恢复操作需由调用方执行
     *
     * @param snapshotId 快照ID
     * @return 快照中的节点和关系数据
     */
    public Map<String, Object> restoreFromSnapshot(Long snapshotId, Long familyId) {
        FamilySnapshot snapshot = snapshotRepository.findById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException("快照不存在");
        }
        if (!Objects.equals(snapshot.getFamilyId(), familyId)) {
            throw new BusinessException("无权恢复该快照");
        }
        return versionControlDomainService.restoreFromSnapshot(snapshotId);
    }

    /**
     * 统计家族快照数量
     *
     * @param familyId 家族ID
     * @return 快照数量
     */
    public long countSnapshots(Long familyId) {
        return snapshotRepository.countByFamilyId(familyId);
    }
}
