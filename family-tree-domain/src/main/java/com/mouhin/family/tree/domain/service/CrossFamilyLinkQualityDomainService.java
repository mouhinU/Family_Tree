package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.CrossFamilyLinkIssueEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 跨族节点链接数据质量领域服务
 *
 * <p>只判定 link 自身及其端点引用的一致性，不修改数据，也不参与族内建树与亲缘算法。</p>
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Service
public class CrossFamilyLinkQualityDomainService {

    /**
     * 判定单条跨族链接的数据质量问题
     *
     * @param link            跨族链接
     * @param srcNode         源端节点，不存在时为 null
     * @param dstNode         目标端节点，不存在时为 null
     * @param srcFamilyExists 源端家族是否存在
     * @param dstFamilyExists 目标端家族是否存在
     * @return 按枚举顺序排列的问题列表，无问题时返回空列表
     */
    public List<CrossFamilyLinkIssueEnum> inspect(CrossFamilyLink link, FamilyNode srcNode, FamilyNode dstNode,
                                                   boolean srcFamilyExists, boolean dstFamilyExists) {
        Set<CrossFamilyLinkIssueEnum> issues = EnumSet.noneOf(CrossFamilyLinkIssueEnum.class);
        if (link == null) {
            return new ArrayList<>();
        }
        if (srcNode == null || dstNode == null) {
            issues.add(CrossFamilyLinkIssueEnum.ORPHAN_NODE);
        }
        if (!srcFamilyExists || !dstFamilyExists) {
            issues.add(CrossFamilyLinkIssueEnum.FAMILY_MISSING);
        }
        if (srcNode != null && !Objects.equals(srcNode.getFamilyId(), link.getSrcFamilyId())) {
            issues.add(CrossFamilyLinkIssueEnum.FAMILY_MISMATCH);
        }
        if (dstNode != null && !Objects.equals(dstNode.getFamilyId(), link.getDstFamilyId())) {
            issues.add(CrossFamilyLinkIssueEnum.FAMILY_MISMATCH);
        }
        if (isSameFamily(link, srcNode, dstNode)) {
            issues.add(CrossFamilyLinkIssueEnum.SAME_FAMILY_ENDPOINT);
        }
        if (!isValidType(link.getLinkType())) {
            issues.add(CrossFamilyLinkIssueEnum.INVALID_LINK_TYPE);
        }
        if (!isValidSource(link.getSource())) {
            issues.add(CrossFamilyLinkIssueEnum.INVALID_SOURCE);
        }
        if (isUnordered(link)) {
            issues.add(CrossFamilyLinkIssueEnum.UNORDERED_ENDPOINT);
        }
        if (isMissingConfirmer(link)) {
            issues.add(CrossFamilyLinkIssueEnum.MISSING_CONFIRMER);
        }
        return new ArrayList<>(issues);
    }

    private boolean isSameFamily(CrossFamilyLink link, FamilyNode srcNode, FamilyNode dstNode) {
        if (Objects.equals(link.getSrcFamilyId(), link.getDstFamilyId())) {
            return true;
        }
        return srcNode != null && dstNode != null && Objects.equals(srcNode.getFamilyId(), dstNode.getFamilyId());
    }

    private boolean isValidType(Integer code) {
        for (NodeLinkTypeEnum value : NodeLinkTypeEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidSource(Integer code) {
        for (NodeLinkSourceEnum value : NodeLinkSourceEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnordered(CrossFamilyLink link) {
        return link.getSrcNodeId() != null && link.getDstNodeId() != null
                && link.getSrcNodeId() >= link.getDstNodeId();
    }

    private boolean isMissingConfirmer(CrossFamilyLink link) {
        return link.getSource() != null
                && !Objects.equals(link.getSource(), NodeLinkSourceEnum.ACCOUNT.getCode())
                && link.getConfirmedBy() == null;
    }
}
