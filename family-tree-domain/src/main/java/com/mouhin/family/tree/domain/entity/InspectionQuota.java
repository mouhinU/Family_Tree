package com.mouhin.family.tree.domain.entity;

/**
 * 巡检配额上下文：应用层按平台生效配置解析出的规模上限，供配额类巡检项比对存量数据。
 *
 * <p>只读值对象。家族级覆盖（如 MAX_NODE_COUNT 的单族覆盖）由探测语句在库内比对，
 * 此处仅承载平台生效值作为缺省上限。</p>
 *
 * @author Family-Tree
 * @date 2026-11-25
 */
public final class InspectionQuota {

    /**
     * 单家族节点数上限（平台生效值，家族覆盖在探测语句内叠加）
     */
    private final int maxNodeCount;

    /**
     * 单家族成员数上限（平台独有配置，无家族覆盖层）
     */
    private final int maxFamilyMembers;

    public InspectionQuota(int maxNodeCount, int maxFamilyMembers) {
        this.maxNodeCount = maxNodeCount;
        this.maxFamilyMembers = maxFamilyMembers;
    }

    public int getMaxNodeCount() {
        return maxNodeCount;
    }

    public int getMaxFamilyMembers() {
        return maxFamilyMembers;
    }

    @Override
    public String toString() {
        return "InspectionQuota{maxNodeCount=" + maxNodeCount
                + ", maxFamilyMembers=" + maxFamilyMembers + '}';
    }
}
