package com.mouhin.family.tree.infrastructure.converter;

import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.entity.InspectionSample;
import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 数据巡检结果转换器
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
public final class InspectionConverter {

    private InspectionConverter() {
    }

    /**
     * 探测结果 DO 列表转巡检结论
     *
     * <p>窗口计数在每一行上重复，取首行即可；无行时视为无异常。</p>
     *
     * @param rows 探测结果
     * @return 巡检结论
     */
    public static InspectionFinding toFinding(List<InspectionSampleDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return InspectionFinding.empty();
        }
        Long total = rows.get(0).getTotal();
        List<InspectionSample> samples = new ArrayList<>(rows.size());
        for (InspectionSampleDO row : rows) {
            samples.add(new InspectionSample(row.getRefId(), row.getSubject(), row.getDetail()));
        }
        return new InspectionFinding(total == null ? samples.size() : total, Collections.unmodifiableList(samples));
    }
}
