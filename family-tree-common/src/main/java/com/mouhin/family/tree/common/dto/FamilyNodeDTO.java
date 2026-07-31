package com.mouhin.family.tree.common.dto;

import lombok.Data;

/**
 * 族谱节点数据传输对象
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Data
public class FamilyNodeDTO {

    private Long id;
    private String name;
    private Integer gender;
    private String birthDate;
    private String deathDate;
    private Integer generation;
    private Integer birthOrder;
    private String colorLabel;
    private String avatar;
    private String remark;
}
