package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 平台跨族图谱节点视图对象
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Getter
@Setter
public class AdminGraphNodeVO {

    private Long id;
    private Long familyId;
    private String familyName;
    private String name;
    private Integer gender;
    private String genderLabel;
    private Integer birthYear;
    private Integer deathYear;
    private Integer generation;
    private Boolean missing;

    @Override
    public String toString() {
        return "AdminGraphNodeVO{"
                + "id=" + id
                + ", familyId=" + familyId
                + ", familyName='" + familyName + '\''
                + ", name='" + name + '\''
                + ", gender=" + gender
                + ", birthYear=" + birthYear
                + ", deathYear=" + deathYear
                + ", generation=" + generation
                + ", missing=" + missing
                + '}';
    }
}
