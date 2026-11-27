package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台跨族图谱链接问题视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class CrossFamilyLinkIssueVO {

    private String code;
    private String description;

    @Override
    public String toString() {
        return "CrossFamilyLinkIssueVO{"
                + "code='" + code + '\''
                + ", description='" + description + '\''
                + '}';
    }
}
