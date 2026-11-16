package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.InspectionItemVO;
import com.mouhin.family.tree.common.dto.InspectionReportVO;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.InspectionSeverityEnum;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.entity.InspectionSample;
import com.mouhin.family.tree.domain.repository.PlatformInspectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 数据巡检应用服务单元测试：覆盖全量探测的调用次数、按级别汇总、样本截断标记与报告字段。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class DataInspectionApplicationServiceTest {

    private static final int EXPECTED_SAMPLE_LIMIT = 10;
    private static final Long REF_ID = 88L;
    private static final String SUBJECT = "mengziyu(蒙子羽)";
    private static final String DETAIL = "current_family_id=3 已无成员关系";

    @Mock
    private PlatformInspectionRepository inspectionRepository;

    private DataInspectionApplicationService service() {
        return new DataInspectionApplicationService(inspectionRepository);
    }

    @Test
    void inspectAll_cleanDatabase_reportsEveryCheckPassed() {
        when(inspectionRepository.findFinding(any(InspectionCheckEnum.class), anyInt()))
                .thenReturn(InspectionFinding.empty());

        InspectionReportVO report = service().inspectAll();

        InspectionCheckEnum[] checks = InspectionCheckEnum.values();
        assertEquals(checks.length, report.getCheckCount().intValue());
        assertEquals(checks.length, report.getItems().size());
        assertEquals(0, report.getProblemCheckCount().intValue());
        assertEquals(0L, report.getHighCount().longValue());
        assertEquals(0L, report.getMediumCount().longValue());
        assertEquals(0L, report.getLowCount().longValue());
        assertEquals(EXPECTED_SAMPLE_LIMIT, report.getSampleLimit().intValue());
        assertNotNull(report.getGeneratedTime());
        for (InspectionCheckEnum check : checks) {
            verify(inspectionRepository).findFinding(check, EXPECTED_SAMPLE_LIMIT);
        }
        InspectionItemVO first = report.getItems().get(0);
        assertEquals(InspectionCheckEnum.ACCOUNT_FAMILY_MISMATCH.getCode(), first.getCode());
        assertEquals(0L, first.getTotal().longValue());
        assertFalse(first.getTruncated());
        assertTrue(first.getSamples().isEmpty());
    }

    @Test
    void inspectAll_mixedFindings_tallyBySeverityAndMarkTruncated() {
        when(inspectionRepository.findFinding(any(InspectionCheckEnum.class), anyInt()))
                .thenReturn(InspectionFinding.empty());
        when(inspectionRepository.findFinding(eq(InspectionCheckEnum.ACCOUNT_FAMILY_MISMATCH), anyInt()))
                .thenReturn(new InspectionFinding(12L, List.of(new InspectionSample(REF_ID, SUBJECT, DETAIL))));
        when(inspectionRepository.findFinding(eq(InspectionCheckEnum.AUTO_ACCOUNT_PLACEHOLDER), anyInt()))
                .thenReturn(new InspectionFinding(1L, List.of(new InspectionSample(REF_ID, SUBJECT, DETAIL))));

        InspectionReportVO report = service().inspectAll();

        assertEquals(2, report.getProblemCheckCount().intValue());
        assertEquals(12L, report.getHighCount().longValue());
        assertEquals(0L, report.getMediumCount().longValue());
        assertEquals(1L, report.getLowCount().longValue());

        InspectionItemVO mismatch = findItem(report, InspectionCheckEnum.ACCOUNT_FAMILY_MISMATCH);
        assertEquals(12L, mismatch.getTotal().longValue());
        assertTrue(mismatch.getTruncated(), "总数大于样本数时应标记截断");
        assertEquals(InspectionSeverityEnum.HIGH.getCode(), mismatch.getSeverity());
        assertEquals(InspectionSeverityEnum.HIGH.getLabel(), mismatch.getSeverityLabel());
        assertEquals(1, mismatch.getSamples().size());
        assertEquals(SUBJECT, mismatch.getSamples().get(0).getSubject());
        assertEquals(DETAIL, mismatch.getSamples().get(0).getDetail());
        assertEquals(REF_ID, mismatch.getSamples().get(0).getRefId());

        InspectionItemVO placeholder = findItem(report, InspectionCheckEnum.AUTO_ACCOUNT_PLACEHOLDER);
        assertEquals(1L, placeholder.getTotal().longValue());
        assertFalse(placeholder.getTruncated(), "样本已覆盖全部异常时不应标记截断");
    }

    @Test
    void inspectAll_mediumOnlyFinding_countedIntoMediumBucketOnly() {
        when(inspectionRepository.findFinding(any(InspectionCheckEnum.class), anyInt()))
                .thenReturn(InspectionFinding.empty());
        when(inspectionRepository.findFinding(eq(InspectionCheckEnum.RELATION_SELF_LOOP), anyInt()))
                .thenReturn(new InspectionFinding(3L, List.of()));

        InspectionReportVO report = service().inspectAll();

        assertEquals(3L, report.getMediumCount().longValue());
        assertEquals(0L, report.getHighCount().longValue());
        assertEquals(0L, report.getLowCount().longValue());
        assertEquals(1, report.getProblemCheckCount().intValue());
        assertTrue(findItem(report, InspectionCheckEnum.RELATION_SELF_LOOP).getTruncated());
    }

    private InspectionItemVO findItem(InspectionReportVO report, InspectionCheckEnum check) {
        return report.getItems().stream()
                .filter(item -> check.getCode().equals(item.getCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少巡检项：" + check.getCode()));
    }
}
