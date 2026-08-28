package com.mouhin.family.tree.domain.gedcom;

import lombok.Getter;
import lombok.Setter;

/**
 * GEDCOM 个人记录中间结构
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class GedcomIndividual {

    /** GEDCOM 引用标识（如 @I1@） */
    private String xref;

    /** 姓名 */
    private String name;

    /** 性别：M=男, F=女, 其他=未知 */
    private String sex;

    /** 出生日期（原始字符串） */
    private String birthDate;

    /** 去世日期（原始字符串） */
    private String deathDate;

    /** 备注 */
    private String note;

    /** 字 */
    private String zi;

    /** 号 */
    private String hao;

    /** 讳 */
    private String hui;
}
