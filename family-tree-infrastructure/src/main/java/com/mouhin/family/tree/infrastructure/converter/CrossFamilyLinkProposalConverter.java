package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposal;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalEvent;
import com.mouhin.family.tree.domain.entity.CrossFamilyLinkProposalVote;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalEventDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyNodeLinkProposalVoteDO;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 跨族链接提案聚合对象转换器
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
public final class CrossFamilyLinkProposalConverter {

    private CrossFamilyLinkProposalConverter() {
    }

    public static CrossFamilyLinkProposal toDomain(FamilyNodeLinkProposalDO doObj) {
        if (doObj == null) {
            return null;
        }
        CrossFamilyLinkProposal entity = new CrossFamilyLinkProposal();
        entity.setId(doObj.getId());
        entity.setSrcFamilyId(doObj.getSrcFamilyId());
        entity.setSrcNodeId(doObj.getSrcNodeId());
        entity.setDstFamilyId(doObj.getDstFamilyId());
        entity.setDstNodeId(doObj.getDstNodeId());
        entity.setLinkType(doObj.getLinkType());
        entity.setStatus(doObj.getStatus());
        entity.setSrcRequirement(doObj.getSrcRequirement());
        entity.setDstRequirement(doObj.getDstRequirement());
        entity.setActiveSrcNodeId(doObj.getActiveSrcNodeId());
        entity.setActiveDstNodeId(doObj.getActiveDstNodeId());
        entity.setInitiatorId(doObj.getInitiatorId());
        entity.setInitiatorFamilyId(doObj.getInitiatorFamilyId());
        entity.setReason(doObj.getReason());
        entity.setReviewComment(doObj.getReviewComment());
        entity.setReviewedBy(doObj.getReviewedBy());
        entity.setReviewedAt(doObj.getReviewedAt());
        entity.setExpireAt(doObj.getExpireAt());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setUpdateTime(doObj.getUpdateTime());
        return entity;
    }

    public static FamilyNodeLinkProposalDO toDO(CrossFamilyLinkProposal entity) {
        if (entity == null) {
            return null;
        }
        FamilyNodeLinkProposalDO doObj = new FamilyNodeLinkProposalDO();
        doObj.setId(entity.getId());
        doObj.setSrcFamilyId(entity.getSrcFamilyId());
        doObj.setSrcNodeId(entity.getSrcNodeId());
        doObj.setDstFamilyId(entity.getDstFamilyId());
        doObj.setDstNodeId(entity.getDstNodeId());
        doObj.setLinkType(entity.getLinkType());
        doObj.setStatus(entity.getStatus());
        doObj.setSrcRequirement(entity.getSrcRequirement());
        doObj.setDstRequirement(entity.getDstRequirement());
        doObj.setActiveSrcNodeId(entity.getActiveSrcNodeId());
        doObj.setActiveDstNodeId(entity.getActiveDstNodeId());
        doObj.setInitiatorId(entity.getInitiatorId());
        doObj.setInitiatorFamilyId(entity.getInitiatorFamilyId());
        doObj.setReason(entity.getReason());
        doObj.setReviewComment(entity.getReviewComment());
        doObj.setReviewedBy(entity.getReviewedBy());
        doObj.setReviewedAt(entity.getReviewedAt());
        doObj.setExpireAt(entity.getExpireAt());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setUpdateTime(entity.getUpdateTime());
        return doObj;
    }

    public static List<CrossFamilyLinkProposal> toDomainList(List<FamilyNodeLinkProposalDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream().map(CrossFamilyLinkProposalConverter::toDomain).toList();
    }

    public static CrossFamilyLinkProposalVote toDomain(FamilyNodeLinkProposalVoteDO doObj) {
        if (doObj == null) {
            return null;
        }
        CrossFamilyLinkProposalVote entity = new CrossFamilyLinkProposalVote();
        entity.setId(doObj.getId());
        entity.setProposalId(doObj.getProposalId());
        entity.setFamilyId(doObj.getFamilyId());
        entity.setNodeId(doObj.getNodeId());
        entity.setUserId(doObj.getUserId());
        entity.setStatus(doObj.getStatus());
        entity.setCreateTime(doObj.getCreateTime());
        entity.setUpdateTime(doObj.getUpdateTime());
        return entity;
    }

    public static FamilyNodeLinkProposalVoteDO toDO(CrossFamilyLinkProposalVote entity) {
        if (entity == null) {
            return null;
        }
        FamilyNodeLinkProposalVoteDO doObj = new FamilyNodeLinkProposalVoteDO();
        doObj.setId(entity.getId());
        doObj.setProposalId(entity.getProposalId());
        doObj.setFamilyId(entity.getFamilyId());
        doObj.setNodeId(entity.getNodeId());
        doObj.setUserId(entity.getUserId());
        doObj.setStatus(entity.getStatus());
        doObj.setCreateTime(entity.getCreateTime());
        doObj.setUpdateTime(entity.getUpdateTime());
        return doObj;
    }

    public static List<CrossFamilyLinkProposalVote> toVoteDomainList(List<FamilyNodeLinkProposalVoteDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream().map(CrossFamilyLinkProposalConverter::toDomain).collect(Collectors.toList());
    }

    public static CrossFamilyLinkProposalEvent toDomain(FamilyNodeLinkProposalEventDO doObj) {
        if (doObj == null) {
            return null;
        }
        CrossFamilyLinkProposalEvent entity = new CrossFamilyLinkProposalEvent();
        entity.setId(doObj.getId());
        entity.setProposalId(doObj.getProposalId());
        entity.setEventType(doObj.getEventType());
        entity.setFromStatus(doObj.getFromStatus());
        entity.setToStatus(doObj.getToStatus());
        entity.setOperatorId(doObj.getOperatorId());
        entity.setOperatorFamilyId(doObj.getOperatorFamilyId());
        entity.setComment(doObj.getComment());
        entity.setIpAddress(doObj.getIpAddress());
        entity.setCreateTime(doObj.getCreateTime());
        return entity;
    }

    public static FamilyNodeLinkProposalEventDO toDO(CrossFamilyLinkProposalEvent entity) {
        if (entity == null) {
            return null;
        }
        FamilyNodeLinkProposalEventDO doObj = new FamilyNodeLinkProposalEventDO();
        doObj.setId(entity.getId());
        doObj.setProposalId(entity.getProposalId());
        doObj.setEventType(entity.getEventType());
        doObj.setFromStatus(entity.getFromStatus());
        doObj.setToStatus(entity.getToStatus());
        doObj.setOperatorId(entity.getOperatorId());
        doObj.setOperatorFamilyId(entity.getOperatorFamilyId());
        doObj.setComment(entity.getComment());
        doObj.setIpAddress(entity.getIpAddress());
        doObj.setCreateTime(entity.getCreateTime());
        return doObj;
    }

    public static List<CrossFamilyLinkProposalEvent> toEventDomainList(
            List<FamilyNodeLinkProposalEventDO> doList) {
        if (doList == null) {
            return Collections.emptyList();
        }
        return doList.stream().map(CrossFamilyLinkProposalConverter::toDomain).collect(Collectors.toList());
    }
}
