package com.mouhin.family.tree.domain.gedcom;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * GEDCOM 文件解析结果，包含所有个人和家庭记录
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class GedcomData {

    /** 个人记录列表 */
    private List<GedcomIndividual> individuals = new ArrayList<>();

    /** 家庭记录列表 */
    private List<GedcomFamily> families = new ArrayList<>();
}
