package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.infrastructure.persistence.entity.FamilyCountDO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 家族维度计数转换器
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
public final class FamilyCountConverter {

    private FamilyCountConverter() {
    }

    /**
     * 计数投影列表转家族ID → 计数映射
     *
     * @param counts 计数投影列表，可为空
     * @return 计数映射；入参为空时返回空映射
     */
    public static Map<Long, Long> toMap(List<FamilyCountDO> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> result = new LinkedHashMap<Long, Long>((int) (counts.size() / 0.75) + 1);
        for (FamilyCountDO count : counts) {
            if (count != null && count.getFamilyId() != null) {
                result.put(count.getFamilyId(), count.getTotal() == null ? 0L : count.getTotal());
            }
        }
        return result;
    }
}
