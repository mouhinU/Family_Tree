package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.domain.entity.NodeHistory;
import com.mouhin.family.tree.domain.entity.RelationHistory;
import com.mouhin.family.tree.domain.repository.HistoryRepository;
import com.mouhin.family.tree.infrastructure.converter.NodeHistoryConverter;
import com.mouhin.family.tree.infrastructure.converter.RelationHistoryConverter;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeHistoryDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.RelationHistoryDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.NodeHistoryMapper;
import com.mouhin.family.tree.infrastructure.persistence.mapper.RelationHistoryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 历史记录仓储实现
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Repository
public class HistoryRepositoryImpl implements HistoryRepository {

    private final NodeHistoryMapper nodeHistoryMapper;
    private final RelationHistoryMapper relationHistoryMapper;

    public HistoryRepositoryImpl(NodeHistoryMapper nodeHistoryMapper,
                                 RelationHistoryMapper relationHistoryMapper) {
        this.nodeHistoryMapper = nodeHistoryMapper;
        this.relationHistoryMapper = relationHistoryMapper;
    }

    @Override
    public NodeHistory saveNodeHistory(NodeHistory history) {
        NodeHistoryDO doObj = NodeHistoryConverter.toDO(history);
        nodeHistoryMapper.insert(doObj);
        history.setId(doObj.getId());
        return history;
    }

    @Override
    public RelationHistory saveRelationHistory(RelationHistory history) {
        RelationHistoryDO doObj = RelationHistoryConverter.toDO(history);
        relationHistoryMapper.insert(doObj);
        history.setId(doObj.getId());
        return history;
    }

    @Override
    public List<NodeHistory> findNodeHistoryByNodeId(Long nodeId, Long familyId, int offset, int limit) {
        LambdaQueryWrapper<NodeHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NodeHistoryDO::getNodeId, nodeId)
                .eq(NodeHistoryDO::getFamilyId, familyId)
                .orderByDesc(NodeHistoryDO::getCreateTime)
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<NodeHistoryDO> doList = nodeHistoryMapper.selectList(wrapper);
        return NodeHistoryConverter.toDomainList(doList);
    }

    @Override
    public long countNodeHistoryByNodeId(Long nodeId, Long familyId) {
        LambdaQueryWrapper<NodeHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NodeHistoryDO::getNodeId, nodeId)
                .eq(NodeHistoryDO::getFamilyId, familyId);
        return nodeHistoryMapper.selectCount(wrapper);
    }

    @Override
    public List<RelationHistory> findRelationHistoryByRelationId(Long relationId, Long familyId, int offset, int limit) {
        LambdaQueryWrapper<RelationHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RelationHistoryDO::getRelationId, relationId)
                .eq(RelationHistoryDO::getFamilyId, familyId)
                .orderByDesc(RelationHistoryDO::getCreateTime)
                .last("LIMIT " + limit + " OFFSET " + offset);
        List<RelationHistoryDO> doList = relationHistoryMapper.selectList(wrapper);
        return RelationHistoryConverter.toDomainList(doList);
    }

    @Override
    public long countRelationHistoryByRelationId(Long relationId, Long familyId) {
        LambdaQueryWrapper<RelationHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RelationHistoryDO::getRelationId, relationId)
                .eq(RelationHistoryDO::getFamilyId, familyId);
        return relationHistoryMapper.selectCount(wrapper);
    }

    @Override
    public NodeHistory findNodeHistoryByVersion(Long nodeId, Integer versionNumber) {
        LambdaQueryWrapper<NodeHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NodeHistoryDO::getNodeId, nodeId)
                .eq(NodeHistoryDO::getVersionNumber, versionNumber);
        NodeHistoryDO doObj = nodeHistoryMapper.selectOne(wrapper);
        return NodeHistoryConverter.toDomain(doObj);
    }

    @Override
    public RelationHistory findRelationHistoryByVersion(Long relationId, Integer versionNumber) {
        LambdaQueryWrapper<RelationHistoryDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RelationHistoryDO::getRelationId, relationId)
                .eq(RelationHistoryDO::getVersionNumber, versionNumber);
        RelationHistoryDO doObj = relationHistoryMapper.selectOne(wrapper);
        return RelationHistoryConverter.toDomain(doObj);
    }

    @Override
    public int getNextNodeVersion(Long nodeId) {
        return nodeHistoryMapper.getNextVersion(nodeId);
    }

    @Override
    public int getNextRelationVersion(Long relationId) {
        return relationHistoryMapper.getNextVersion(relationId);
    }
}
