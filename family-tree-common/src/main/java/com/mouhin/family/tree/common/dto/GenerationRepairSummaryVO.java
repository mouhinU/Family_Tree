package com.mouhin.family.tree.common.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 平台侧全量世代修复结果视图对象
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
@Getter
@Setter
public class GenerationRepairSummaryVO {

    /**
     * 扫描家族总数
     */
    private Integer scannedFamilies;

    /**
     * 实际更新过节点的家族数
     */
    private Integer repairedFamilies;

    /**
     * 因已维护辈分表而跳过的家族数
     */
    private Integer skippedManagedFamilies;

    /**
     * 结构一致、无需变更的家族数
     */
    private Integer unchangedFamilies;

    /**
     * 修复失败的家族数
     */
    private Integer failedFamilies;

    /**
     * 更新节点总数
     */
    private Integer updatedNodes;

    /**
     * 失败家族的简要原因列表
     */
    private List<String> failures;
}
