package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 平台跨族图谱单家族下钻视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphDetailVO {

    private AdminGraphFamilyVO family;
    private List<AdminGraphNodeVO> nodes;
    private List<AdminGraphLinkVO> links;
    private Boolean truncated;

    @Override
    public String toString() {
        return "AdminGraphDetailVO{"
                + "family=" + family
                + ", nodes=" + (nodes == null ? 0 : nodes.size())
                + ", links=" + (links == null ? 0 : links.size())
                + ", truncated=" + truncated
                + '}';
    }
}
