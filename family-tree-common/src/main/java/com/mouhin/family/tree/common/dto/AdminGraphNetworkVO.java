package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台展示型跨族图谱视图对象。
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphNetworkVO {

    private AdminGraphFamilyVO family;
    private List<AdminGraphNodeVO> nodes = new ArrayList<>();
    private List<AdminGraphRelationVO> relations = new ArrayList<>();
    private List<AdminGraphLinkVO> crossFamilyLinks = new ArrayList<>();
    private Boolean truncated;
}
