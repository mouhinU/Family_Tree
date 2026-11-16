package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformInspectionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 数据巡检仓储实现单元测试：覆盖巡检项到探测语句的分派、枚举编码传参、结果转换与入参保护。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class PlatformInspectionRepositoryImplTest {

    private static final int SAMPLE_LIMIT = 10;
    private static final Long REF_ID = 66L;
    private static final String SUBJECT = "蒙氏家族";
    private static final String DETAIL = "成员 0 人，无族长";

    @Mock
    private PlatformInspectionMapper mapper;

    private PlatformInspectionRepositoryImpl repository() {
        return new PlatformInspectionRepositoryImpl(mapper);
    }

    @Test
    void findFinding_familyWithoutOwner_passesOwnerRoleCode() {
        when(mapper.listFamilyWithoutOwner(FamilyMemberRoleEnum.OWNER.getCode(), SAMPLE_LIMIT))
                .thenReturn(List.of(row(1)));

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.FAMILY_WITHOUT_OWNER, SAMPLE_LIMIT);

        assertEquals(1L, finding.getTotal());
        assertEquals(REF_ID, finding.getSamples().get(0).getRefId());
        assertEquals(SUBJECT, finding.getSamples().get(0).getSubject());
        verify(mapper).listFamilyWithoutOwner(FamilyMemberRoleEnum.OWNER.getCode(), SAMPLE_LIMIT);
    }

    @Test
    void findFinding_generationInverted_passesParentChildTypeCode() {
        when(mapper.listGenerationInverted(RelationTypeEnum.PARENT_CHILD.getCode(), SAMPLE_LIMIT))
                .thenReturn(Collections.emptyList());

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.GENERATION_INVERTED, SAMPLE_LIMIT);

        assertEquals(0L, finding.getTotal());
        assertTrue(finding.getSamples().isEmpty());
        verify(mapper).listGenerationInverted(RelationTypeEnum.PARENT_CHILD.getCode(), SAMPLE_LIMIT);
    }

    @Test
    void findFinding_spouseCrossFamily_passesSpouseTypeCode() {
        when(mapper.listSpouseCrossFamily(RelationTypeEnum.SPOUSE.getCode(), SAMPLE_LIMIT))
                .thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.SPOUSE_CROSS_FAMILY, SAMPLE_LIMIT);

        verify(mapper).listSpouseCrossFamily(RelationTypeEnum.SPOUSE.getCode(), SAMPLE_LIMIT);
    }

    @Test
    void findFinding_autoAccountPlaceholder_passesBooleanFlag() {
        when(mapper.listAutoAccountPlaceholder(true, SAMPLE_LIMIT)).thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.AUTO_ACCOUNT_PLACEHOLDER, SAMPLE_LIMIT);

        verify(mapper).listAutoAccountPlaceholder(true, SAMPLE_LIMIT);
    }

    @Test
    void findFinding_windowTotalMissing_fallsBackToSampleCount() {
        InspectionSampleDO row = row(7);
        row.setTotal(null);
        when(mapper.listMemberOrphanFamily(SAMPLE_LIMIT)).thenReturn(List.of(row));

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.MEMBER_ORPHAN_FAMILY, SAMPLE_LIMIT);

        assertEquals(1L, finding.getTotal());
    }

    @Test
    void findFinding_nullCheck_throwsWithoutQuery() {
        assertThrows(BusinessException.class, () -> repository().findFinding(null, SAMPLE_LIMIT));
        verifyNoInteractions(mapper);
    }

    @Test
    void findFinding_sampleLimitIsPassedThrough() {
        when(mapper.listRelationOrphanNode(anyInt())).thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.RELATION_ORPHAN_NODE, 3);

        verify(mapper).listRelationOrphanNode(3);
    }

    private InspectionSampleDO row(long total) {
        InspectionSampleDO row = new InspectionSampleDO();
        row.setRefId(REF_ID);
        row.setSubject(SUBJECT);
        row.setDetail(DETAIL);
        row.setTotal(total);
        return row;
    }
}
