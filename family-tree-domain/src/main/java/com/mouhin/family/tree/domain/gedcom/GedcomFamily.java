package com.mouhin.family.tree.domain.gedcom;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * GEDCOM 家庭记录中间结构
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class GedcomFamily {

    /** GEDCOM 引用标识（如 @F1@） */
    private String xref;

    /** 丈夫个人引用标识 */
    private String husbXref;

    /** 妻子个人引用标识 */
    private String wifeXref;

    /** 子女个人引用标识列表 */
    private List<String> childrenXrefs = new ArrayList<>();

    /** 结婚日期（原始字符串） */
    private String marriageDate;

    /** 离异日期（原始字符串） */
    private String divorceDate;
}
