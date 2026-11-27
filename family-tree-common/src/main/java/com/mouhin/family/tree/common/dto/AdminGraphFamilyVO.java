package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台跨族图谱家族节点视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphFamilyVO {

    private Long id;
    private String name;
    private String status;
    private Long linkCount;
    private Long bloodLinkCount;
    private Long marriageLinkCount;
    private Long issueCount;

    @Override
    public String toString() {
        return "AdminGraphFamilyVO{"
                + "id=" + id
                + ", name='" + name + '\''
                + ", status='" + status + '\''
                + ", linkCount=" + linkCount
                + ", bloodLinkCount=" + bloodLinkCount
                + ", marriageLinkCount=" + marriageLinkCount
                + ", issueCount=" + issueCount
                + '}';
    }
}
