package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.infrastructure.persistence.entity.UserCountDO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户维度计数转换器
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class UserCountConverter {

    private UserCountConverter() {
    }

    /**
     * 计数投影列表转用户ID → 计数映射
     *
     * @param counts 计数投影列表，可为空
     * @return 计数映射；入参为空时返回空映射
     */
    public static Map<Long, Long> toMap(List<UserCountDO> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> result = new LinkedHashMap<Long, Long>((int) (counts.size() / 0.75) + 1);
        for (UserCountDO count : counts) {
            if (count != null && count.getUserId() != null) {
                result.put(count.getUserId(), count.getTotal() == null ? 0L : count.getTotal());
            }
        }
        return result;
    }
}
