package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.CrossFamilyLinkProposalStatusEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨族链接提案聚合根
 *
 * @author Family-Tree
 * @date 2026-11-28
 */
@Getter
@Setter
public class CrossFamilyLinkProposal {

    private Long id;
    private Long srcFamilyId;
    private Long srcNodeId;
    private Long dstFamilyId;
    private Long dstNodeId;
    private Integer linkType;
    private String status;
    private Integer srcRequirement;
    private Integer dstRequirement;
    private Long activeSrcNodeId;
    private Long activeDstNodeId;
    private Long initiatorId;
    private Long initiatorFamilyId;
    private String reason;
    private String reviewComment;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验提案端点与门槛快照
     */
    public void validate() {
        if (srcFamilyId == null || srcNodeId == null || dstFamilyId == null || dstNodeId == null) {
            throw new BusinessException("跨族链接提案缺少家族或节点ID");
        }
        if (Objects.equals(srcFamilyId, dstFamilyId) || Objects.equals(srcNodeId, dstNodeId)) {
            throw new BusinessException("跨族链接提案两端必须属于不同家族和不同节点");
        }
        if (linkType == null) {
            throw new BusinessException("跨族链接提案缺少链接类型");
        }
        if (srcRequirement == null || srcRequirement < 1 || dstRequirement == null || dstRequirement < 1) {
            throw new BusinessException("跨族链接提案同意门槛无效");
        }
        if (initiatorId == null || initiatorFamilyId == null || expireAt == null) {
            throw new BusinessException("跨族链接提案缺少发起人或到期时间");
        }
    }

    /**
     * 按节点ID升序规范化端点
     */
    public void orderEndpoints() {
        if (srcNodeId == null || dstNodeId == null || srcNodeId <= dstNodeId) {
            return;
        }
        Long nodeId = srcNodeId;
        Long familyId = srcFamilyId;
        Integer requirement = srcRequirement;
        srcNodeId = dstNodeId;
        srcFamilyId = dstFamilyId;
        srcRequirement = dstRequirement;
        dstNodeId = nodeId;
        dstFamilyId = familyId;
        dstRequirement = requirement;
    }

    /**
     * 判断提案是否处于活跃状态
     *
     * @return 是否仍允许推进流程
     */
    public boolean isActive() {
        return status != null && CrossFamilyLinkProposalStatusEnum.fromCode(status).isActive();
    }

    /**
     * 判断是否允许继续投票
     *
     * @param now 当前时间
     */
    public void requireVotingOpen(LocalDateTime now) {
        if (!CrossFamilyLinkProposalStatusEnum.PENDING_VOTES.getCode().equals(status)) {
            throw new BusinessException("提案当前不在投票阶段");
        }
        if (expireAt == null || !now.isBefore(expireAt)) {
            throw new BusinessException("提案已过期");
        }
    }

    /**
     * 判断是否允许平台审核
     */
    public void requireReviewable() {
        if (!CrossFamilyLinkProposalStatusEnum.READY_FOR_REVIEW.getCode().equals(status)) {
            throw new BusinessException("提案尚未满足审核条件");
        }
    }

    /**
     * 变更提案状态并维护活跃唯一键
     *
     * @param target 目标状态
     * @param now    变更时间
     */
    public void changeStatus(CrossFamilyLinkProposalStatusEnum target, LocalDateTime now) {
        if (target == null) {
            throw new BusinessException("缺少提案目标状态");
        }
        status = target.getCode();
        activeSrcNodeId = target.isActive() ? srcNodeId : null;
        activeDstNodeId = target.isActive() ? dstNodeId : null;
        updateTime = now;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossFamilyLinkProposal that = (CrossFamilyLinkProposal) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CrossFamilyLinkProposal{"
                + "id=" + id
                + ", srcFamilyId=" + srcFamilyId
                + ", srcNodeId=" + srcNodeId
                + ", dstFamilyId=" + dstFamilyId
                + ", dstNodeId=" + dstNodeId
                + ", status='" + status + '\''
                + ", expireAt=" + expireAt
                + '}';
    }
}
