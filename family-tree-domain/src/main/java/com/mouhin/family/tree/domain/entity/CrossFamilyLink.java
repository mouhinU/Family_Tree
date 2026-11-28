package com.mouhin.family.tree.domain.entity;

import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 跨族节点链接实体（族间身份引用）
 *
 * <p>表达"src 节点与 dst 节点是同一人（或姻亲对应人）"。与 {@link FamilyRelation} 正交：
 * relation 只做族内亲缘边并参与全部族内算法，link 只做族间身份引用，
 * 建树/世代修复/级联/亲属路径等族内算法一律不得读取本实体。</p>
 *
 * <p>两端按节点ID规范化存放（{@code srcNodeId < dstNodeId}），配合唯一键实现双向去重。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Getter
@Setter
public class CrossFamilyLink {

    private Long id;
    private Long srcFamilyId;
    private Long srcNodeId;
    private Long dstFamilyId;
    private Long dstNodeId;

    /**
     * 链接类型（{@link com.mouhin.family.tree.common.enums.NodeLinkTypeEnum} 编码）
     */
    private Integer linkType;

    /**
     * 成链来源（{@link NodeLinkSourceEnum} 编码）
     */
    private Integer source;

    /**
     * 确认人用户ID（账号自动来源为空）
     */
    private Long confirmedBy;

    /**
     * 人工提案ID（账号自动成链为空）
     */
    private Long proposalId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 校验链接数据的完整性：端点齐备、异族约束、类型与来源必填
     */
    public void validate() {
        if (srcFamilyId == null || srcNodeId == null || dstFamilyId == null || dstNodeId == null) {
            throw new BusinessException("跨族链接缺少家族或节点ID");
        }
        if (Objects.equals(srcFamilyId, dstFamilyId)) {
            throw new BusinessException("跨族链接两端必须属于不同家族");
        }
        if (linkType == null) {
            throw new BusinessException("跨族链接缺少链接类型");
        }
        if (source == null) {
            throw new BusinessException("跨族链接缺少成链来源");
        }
    }

    /**
     * 按节点ID升序规范化两端（含所在家族列），保证唯一键 (src_node_id, dst_node_id) 双向去重生效
     */
    public void orderEndpoints() {
        if (srcNodeId == null || dstNodeId == null || srcNodeId <= dstNodeId) {
            return;
        }
        Long nodeId = srcNodeId;
        Long familyId = srcFamilyId;
        srcNodeId = dstNodeId;
        srcFamilyId = dstFamilyId;
        dstNodeId = nodeId;
        dstFamilyId = familyId;
    }

    /**
     * 判断链接是否指向指定节点
     *
     * @param nodeId 节点ID
     * @return 是否为链接端点
     */
    public boolean touchesNode(Long nodeId) {
        return Objects.equals(srcNodeId, nodeId) || Objects.equals(dstNodeId, nodeId);
    }

    /**
     * 获取链接另一端节点ID（给定一端，返回另一方）
     *
     * @param nodeId 已知端节点ID
     * @return 另一端节点ID
     */
    public Long otherNodeId(Long nodeId) {
        return Objects.equals(srcNodeId, nodeId) ? dstNodeId : srcNodeId;
    }

    /**
     * 获取链接另一端家族ID（给定一端，返回另一方）
     *
     * @param familyId 已知端家族ID
     * @return 另一端家族ID
     */
    public Long otherFamilyId(Long familyId) {
        return Objects.equals(srcFamilyId, familyId) ? dstFamilyId : srcFamilyId;
    }

    /**
     * 判断是否为账号自动派生的链接（解绑时需自动释放）
     *
     * @return 来源是否为账号自动
     */
    public boolean isAccountSourced() {
        return Objects.equals(source, NodeLinkSourceEnum.ACCOUNT.getCode());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrossFamilyLink that = (CrossFamilyLink) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "CrossFamilyLink{"
                + "id=" + id
                + ", srcFamilyId=" + srcFamilyId
                + ", srcNodeId=" + srcNodeId
                + ", dstFamilyId=" + dstFamilyId
                + ", dstNodeId=" + dstNodeId
                + ", linkType=" + linkType
                + ", source=" + source
                + ", proposalId=" + proposalId
                + '}';
    }
}
