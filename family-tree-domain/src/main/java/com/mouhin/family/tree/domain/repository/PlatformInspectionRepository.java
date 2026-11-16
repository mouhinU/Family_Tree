package com.mouhin.family.tree.domain.repository;

import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.domain.entity.InspectionFinding;

/**
 * 平台数据巡检仓储：按巡检项探测跨家族的异常数据。
 *
 * <p>只读，不修改任何数据；实现负责把巡检项映射到对应的探测语句。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public interface PlatformInspectionRepository {

    /**
     * 探测指定巡检项的异常数据
     *
     * @param check      巡检项
     * @param sampleLimit 样本明细的最大返回条数
     * @return 异常总数与样本明细；无异常时总数为 0、样本为空
     */
    InspectionFinding findFinding(InspectionCheckEnum check, int sampleLimit);
}
