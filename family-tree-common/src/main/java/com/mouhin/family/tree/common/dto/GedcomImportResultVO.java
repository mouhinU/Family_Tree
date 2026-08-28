package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * GEDCOM 导入结果展示对象
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Getter
@Setter
public class GedcomImportResultVO {

    /** 成功导入的节点数 */
    private int importedNodeCount;

    /** 成功导入的关系数 */
    private int importedRelationCount;

    /** 解析到的个人记录总数 */
    private int parsedIndividualCount;

    /** 解析到的家庭记录总数 */
    private int parsedFamilyCount;

    /** 导入提示信息 */
    private String message;
}
