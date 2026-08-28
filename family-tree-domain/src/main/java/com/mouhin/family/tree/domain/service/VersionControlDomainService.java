package com.mouhin.family.tree.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.entity.FamilySnapshot;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.entity.RelationHistory;
import com.mouhin.family.tree.domain.repository.HistoryRepository;
import com.mouhin.family.tree.domain.repository.SnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 版本控制领域服务
 * <p>
 * 负责节点和关系的版本历史记录、差异对比、快照管理
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Service
public class VersionControlDomainService {

    private static final Logger logger = LoggerFactory.getLogger(VersionControlDomainService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final HistoryRepository historyRepository;
    private final SnapshotRepository snapshotRepository;

    public VersionControlDomainService(HistoryRepository historyRepository,
                                       SnapshotRepository snapshotRepository) {
        this.historyRepository = historyRepository;
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * 记录节点创建历史
     *
     * @param node        节点对象
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordNodeCreate(FamilyNode node, Long operatorId, String operatorName, String ipAddress) {
        NodeHistory history = buildNodeHistory(
                node.getId(),
                node.getFamilyId(),
                "CREATE",
                operatorId,
                operatorName,
                null,
                toJson(node),
                "创建节点: " + node.getName(),
                ipAddress
        );
        historyRepository.saveNodeHistory(history);
    }

    /**
     * 记录节点更新历史
     *
     * @param oldNode     修改前的节点
     * @param newNode     修改后的节点
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordNodeUpdate(FamilyNode oldNode, FamilyNode newNode,
                                  Long operatorId, String operatorName, String ipAddress) {
        String changeSummary = generateNodeChangeSummary(oldNode, newNode);
        NodeHistory history = buildNodeHistory(
                oldNode.getId(),
                oldNode.getFamilyId(),
                "UPDATE",
                operatorId,
                operatorName,
                toJson(oldNode),
                toJson(newNode),
                changeSummary,
                ipAddress
        );
        historyRepository.saveNodeHistory(history);
    }

    /**
     * 记录节点删除历史
     *
     * @param node        被删除的节点
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordNodeDelete(FamilyNode node, Long operatorId, String operatorName, String ipAddress) {
        NodeHistory history = buildNodeHistory(
                node.getId(),
                node.getFamilyId(),
                "DELETE",
                operatorId,
                operatorName,
                toJson(node),
                null,
                "删除节点: " + node.getName(),
                ipAddress
        );
        historyRepository.saveNodeHistory(history);
    }

    /**
     * 记录关系创建历史
     *
     * @param relation    关系对象
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordRelationCreate(FamilyRelation relation, Long operatorId, String operatorName, String ipAddress) {
        RelationHistory history = buildRelationHistory(
                relation.getId(),
                relation.getFamilyId(),
                "CREATE",
                operatorId,
                operatorName,
                null,
                toJson(relation),
                "创建关系",
                ipAddress
        );
        historyRepository.saveRelationHistory(history);
    }

    /**
     * 记录关系更新历史
     *
     * @param oldRelation 修改前的关系
     * @param newRelation 修改后的关系
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordRelationUpdate(FamilyRelation oldRelation, FamilyRelation newRelation,
                                      Long operatorId, String operatorName, String ipAddress) {
        String changeSummary = generateRelationChangeSummary(oldRelation, newRelation);
        RelationHistory history = buildRelationHistory(
                oldRelation.getId(),
                oldRelation.getFamilyId(),
                "UPDATE",
                operatorId,
                operatorName,
                toJson(oldRelation),
                toJson(newRelation),
                changeSummary,
                ipAddress
        );
        historyRepository.saveRelationHistory(history);
    }

    /**
     * 记录关系删除历史
     *
     * @param relation    被删除的关系
     * @param operatorId  操作人ID
     * @param operatorName 操作人姓名
     * @param ipAddress   IP地址
     */
    public void recordRelationDelete(FamilyRelation relation, Long operatorId, String operatorName, String ipAddress) {
        RelationHistory history = buildRelationHistory(
                relation.getId(),
                relation.getFamilyId(),
                "DELETE",
                operatorId,
                operatorName,
                toJson(relation),
                null,
                "删除关系",
                ipAddress
        );
        historyRepository.saveRelationHistory(history);
    }

    /**
     * 生成节点变更摘要
     */
    private String generateNodeChangeSummary(FamilyNode oldNode, FamilyNode newNode) {
        List<String> changes = new ArrayList<>();

        if (!Objects.equals(oldNode.getName(), newNode.getName())) {
            changes.add("姓名: " + oldNode.getName() + " → " + newNode.getName());
        }
        if (!Objects.equals(oldNode.getGender(), newNode.getGender())) {
            changes.add("性别变更");
        }
        if (!Objects.equals(oldNode.getBirthDate(), newNode.getBirthDate())) {
            changes.add("出生日期变更");
        }
        if (!Objects.equals(oldNode.getDeathDate(), newNode.getDeathDate())) {
            changes.add("去世日期变更");
        }
        if (!Objects.equals(oldNode.getGeneration(), newNode.getGeneration())) {
            changes.add("世代变更: " + oldNode.getGeneration() + " → " + newNode.getGeneration());
        }
        if (!Objects.equals(oldNode.getColorLabel(), newNode.getColorLabel())) {
            changes.add("颜色标签变更");
        }

        return changes.isEmpty() ? "更新节点信息" : String.join("; ", changes);
    }

    /**
     * 生成关系变更摘要
     */
    private String generateRelationChangeSummary(FamilyRelation oldRelation, FamilyRelation newRelation) {
        List<String> changes = new ArrayList<>();

        if (!Objects.equals(oldRelation.getRelationType(), newRelation.getRelationType())) {
            changes.add("关系类型变更");
        }
        if (!Objects.equals(oldRelation.getMarriageDate(), newRelation.getMarriageDate())) {
            changes.add("结婚日期变更");
        }
        if (!Objects.equals(oldRelation.getDivorceDate(), newRelation.getDivorceDate())) {
            changes.add("离婚日期变更");
        }
        if (!Objects.equals(oldRelation.getDivorced(), newRelation.getDivorced())) {
            changes.add("离婚状态变更");
        }

        return changes.isEmpty() ? "更新关系信息" : String.join("; ", changes);
    }

    /**
     * 构建节点历史对象
     */
    private NodeHistory buildNodeHistory(Long nodeId, Long familyId, String operationType,
                                          Long operatorId, String operatorName,
                                          String beforeData, String afterData,
                                          String changeSummary, String ipAddress) {
        NodeHistory history = new NodeHistory();
        history.setNodeId(nodeId);
        history.setFamilyId(familyId);
        history.setOperationType(operationType);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setBeforeData(beforeData);
        history.setAfterData(afterData);
        history.setChangeSummary(changeSummary);
        history.setIpAddress(ipAddress);
        history.setVersionNumber(historyRepository.getNextNodeVersion(nodeId));
        return history;
    }

    /**
     * 构建关系历史对象
     */
    private RelationHistory buildRelationHistory(Long relationId, Long familyId, String operationType,
                                                  Long operatorId, String operatorName,
                                                  String beforeData, String afterData,
                                                  String changeSummary, String ipAddress) {
        RelationHistory history = new RelationHistory();
        history.setRelationId(relationId);
        history.setFamilyId(familyId);
        history.setOperationType(operationType);
        history.setOperatorId(operatorId);
        history.setOperatorName(operatorName);
        history.setBeforeData(beforeData);
        history.setAfterData(afterData);
        history.setChangeSummary(changeSummary);
        history.setIpAddress(ipAddress);
        history.setVersionNumber(historyRepository.getNextRelationVersion(relationId));
        return history;
    }

    /**
     * 计算两个版本之间的差异
     *
     * @param version1 版本1的JSON数据
     * @param version2 版本2的JSON数据
     * @return 差异描述Map（字段名 -> [旧值, 新值]）
     */
    public Map<String, Object[]> compareVersions(String version1, String version2) {
        Map<String, Object[]> differences = new HashMap<>();

        try {
            JsonNode node1 = objectMapper.readTree(version1);
            JsonNode node2 = objectMapper.readTree(version2);

            // 遍历所有字段
            Set<String> allFields = new HashSet<>();
            node1.fieldNames().forEachRemaining(allFields::add);
            node2.fieldNames().forEachRemaining(allFields::add);

            for (String field : allFields) {
                JsonNode value1 = node1.get(field);
                JsonNode value2 = node2.get(field);

                if (!Objects.equals(value1, value2)) {
                    differences.put(field, new Object[]{
                            value1 == null ? null : value1.asText(),
                            value2 == null ? null : value2.asText()
                    });
                }
            }
        } catch (Exception e) {
            logger.error("版本对比失败", e);
            throw new BusinessException("版本对比失败: " + e.getMessage());
        }

        return differences;
    }

    /**
     * 创建家族快照
     *
     * @param familyId    家族ID
     * @param snapshotName 快照名称
     * @param description  快照描述
     * @param creatorId    创建人ID
     * @param creatorName  创建人姓名
     * @param nodes        所有节点列表
     * @param relations    所有关系列表
     * @return 创建的快照
     */
    public FamilySnapshot createSnapshot(Long familyId, String snapshotName, String description,
                                          Long creatorId, String creatorName,
                                          List<FamilyNode> nodes, List<FamilyRelation> relations) {
        FamilySnapshot snapshot = new FamilySnapshot();
        snapshot.setFamilyId(familyId);
        snapshot.setSnapshotName(snapshotName);
        snapshot.setDescription(description);
        snapshot.setCreatorId(creatorId);
        snapshot.setCreatorName(creatorName);
        snapshot.setNodeCount(nodes.size());
        snapshot.setRelationCount(relations.size());

        // 序列化快照数据
        try {
            Map<String, Object> snapshotData = new HashMap<>();
            snapshotData.put("nodes", nodes);
            snapshotData.put("relations", relations);
            snapshot.setSnapshotData(objectMapper.writeValueAsString(snapshotData));
        } catch (Exception e) {
            logger.error("序列化快照数据失败", e);
            throw new BusinessException("创建快照失败: " + e.getMessage());
        }

        return snapshotRepository.save(snapshot);
    }

    /**
     * 从快照恢复数据
     *
     * @param snapshotId 快照ID
     * @return 快照中的节点和关系数据
     */
    public Map<String, Object> restoreFromSnapshot(Long snapshotId) {
        FamilySnapshot snapshot = snapshotRepository.findById(snapshotId);
        if (snapshot == null) {
            throw new BusinessException("快照不存在");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(
                    snapshot.getSnapshotData(),
                    Map.class
            );
            return data;
        } catch (Exception e) {
            logger.error("反序列化快照数据失败", e);
            throw new BusinessException("恢复快照失败: " + e.getMessage());
        }
    }

    /**
     * 将对象转换为JSON字符串
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            logger.warn("对象序列化失败", e);
            return null;
        }
    }
}
