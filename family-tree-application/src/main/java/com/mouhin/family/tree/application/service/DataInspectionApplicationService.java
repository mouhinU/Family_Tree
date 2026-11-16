package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.InspectionItemVO;
import com.mouhin.family.tree.common.dto.InspectionReportVO;
import com.mouhin.family.tree.common.dto.InspectionSampleVO;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.InspectionSeverityEnum;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.entity.InspectionSample;
import com.mouhin.family.tree.domain.repository.PlatformInspectionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 平台数据巡检应用服务：逐项执行只读异常探测并汇总为体检报告。
 *
 * <p>本服务只读取数据、不落任何修改，也不记录操作日志；报告中的样本仅用于定位，
 * 修复动作必须由管理员在业务页面自行确认后执行。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@Service
public class DataInspectionApplicationService {

    /**
     * 单个巡检项返回的异常样本条数上限
     */
    private static final int SAMPLE_LIMIT = 10;

    private final PlatformInspectionRepository inspectionRepository;

    public DataInspectionApplicationService(PlatformInspectionRepository inspectionRepository) {
        this.inspectionRepository = inspectionRepository;
    }

    /**
     * 执行全量巡检并生成报告
     *
     * @return 按巡检项定义顺序排列的体检报告
     */
    public InspectionReportVO inspectAll() {
        InspectionCheckEnum[] checks = InspectionCheckEnum.values();
        List<InspectionItemVO> items = new ArrayList<>(checks.length);
        Map<InspectionSeverityEnum, Long> totalsBySeverity = new EnumMap<>(InspectionSeverityEnum.class);
        for (InspectionSeverityEnum severity : InspectionSeverityEnum.values()) {
            totalsBySeverity.put(severity, 0L);
        }
        int problemCheckCount = 0;
        for (InspectionCheckEnum check : checks) {
            InspectionFinding finding = inspectionRepository.findFinding(check, SAMPLE_LIMIT);
            InspectionItemVO item = toItemVO(check, finding);
            items.add(item);
            if (finding.getTotal() > 0L) {
                problemCheckCount++;
                totalsBySeverity.merge(check.getSeverity(), finding.getTotal(), Long::sum);
            }
        }
        return toReportVO(items, problemCheckCount, totalsBySeverity);
    }

    /**
     * 把单项探测结果组装为视图对象
     */
    private InspectionItemVO toItemVO(InspectionCheckEnum check, InspectionFinding finding) {
        List<InspectionSampleVO> samples = new ArrayList<>(finding.getSamples().size());
        for (InspectionSample sample : finding.getSamples()) {
            InspectionSampleVO sampleVO = new InspectionSampleVO();
            sampleVO.setRefId(sample.getRefId());
            sampleVO.setSubject(sample.getSubject());
            sampleVO.setDetail(sample.getDetail());
            samples.add(sampleVO);
        }
        InspectionItemVO item = new InspectionItemVO();
        item.setCode(check.getCode());
        item.setTitle(check.getTitle());
        item.setDescription(check.getDescription());
        item.setSeverity(check.getSeverity().getCode());
        item.setSeverityLabel(check.getSeverity().getLabel());
        item.setTotal(finding.getTotal());
        item.setTruncated(finding.getTotal() > samples.size());
        item.setSamples(samples);
        return item;
    }

    /**
     * 汇总报告头部统计
     */
    private InspectionReportVO toReportVO(List<InspectionItemVO> items, int problemCheckCount,
                                          Map<InspectionSeverityEnum, Long> totalsBySeverity) {
        InspectionReportVO report = new InspectionReportVO();
        report.setGeneratedTime(LocalDateTime.now());
        report.setCheckCount(items.size());
        report.setProblemCheckCount(problemCheckCount);
        report.setHighCount(totalsBySeverity.get(InspectionSeverityEnum.HIGH));
        report.setMediumCount(totalsBySeverity.get(InspectionSeverityEnum.MEDIUM));
        report.setLowCount(totalsBySeverity.get(InspectionSeverityEnum.LOW));
        report.setSampleLimit(SAMPLE_LIMIT);
        report.setItems(items);
        return report;
    }
}
