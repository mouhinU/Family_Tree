package com.mouhin.family.tree.infrastructure.persistence.repository;

import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.enums.FamilySettingKeyEnum;
import com.mouhin.family.tree.common.enums.GenderEnum;
import com.mouhin.family.tree.common.enums.InspectionCheckEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.LunarSolarUtil;
import com.mouhin.family.tree.domain.entity.InspectionFinding;
import com.mouhin.family.tree.domain.entity.InspectionQuota;
import com.mouhin.family.tree.infrastructure.persistence.entity.InspectionSampleDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.NodeLunarRowDO;
import com.mouhin.family.tree.infrastructure.persistence.entity.SettingRowDO;
import com.mouhin.family.tree.infrastructure.persistence.mapper.PlatformInspectionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
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
    private static final int PARENT_CYCLE_MAX_DEPTH = 30;
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

    @Test
    void findFinding_parentCycle_passesParentChildCodeAndDepthGuard() {
        when(mapper.listParentCycle(RelationTypeEnum.PARENT_CHILD.getCode(), PARENT_CYCLE_MAX_DEPTH, SAMPLE_LIMIT))
                .thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.PARENT_CYCLE, SAMPLE_LIMIT);

        verify(mapper).listParentCycle(RelationTypeEnum.PARENT_CHILD.getCode(), PARENT_CYCLE_MAX_DEPTH, SAMPLE_LIMIT);
    }

    @Test
    void findFinding_nodeFieldInvalid_passesGenderRange() {
        when(mapper.listNodeFieldInvalid(GenderEnum.UNKNOWN.getCode(), GenderEnum.FEMALE.getCode(), SAMPLE_LIMIT))
                .thenReturn(List.of(row(1)));

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.NODE_FIELD_INVALID, SAMPLE_LIMIT);

        assertEquals(1L, finding.getTotal());
        verify(mapper).listNodeFieldInvalid(GenderEnum.UNKNOWN.getCode(), GenderEnum.FEMALE.getCode(), SAMPLE_LIMIT);
    }

    @Test
    void findFinding_quotaExceededWithoutContext_throwsWithoutQuery() {
        assertThrows(BusinessException.class,
                () -> repository().findFinding(InspectionCheckEnum.QUOTA_EXCEEDED, SAMPLE_LIMIT));
        verifyNoInteractions(mapper);
    }

    @Test
    void findFinding_quotaExceeded_passesNodeCountKeyAndQuotaValues() {
        when(mapper.listQuotaExceeded(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), 5000, 50, SAMPLE_LIMIT))
                .thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.QUOTA_EXCEEDED, SAMPLE_LIMIT, new InspectionQuota(5000, 50));

        verify(mapper).listQuotaExceeded(FamilySettingKeyEnum.MAX_NODE_COUNT.getCode(), 5000, 50, SAMPLE_LIMIT);
    }

    @Test
    void findFinding_relationFlagDateMismatch_passesSpouseCodeAndBooleanFlag() {
        when(mapper.listRelationFlagDateMismatch(RelationTypeEnum.SPOUSE.getCode(), true, SAMPLE_LIMIT))
                .thenReturn(Collections.emptyList());

        repository().findFinding(InspectionCheckEnum.RELATION_FLAG_DATE_MISMATCH, SAMPLE_LIMIT);

        verify(mapper).listRelationFlagDateMismatch(RelationTypeEnum.SPOUSE.getCode(), true, SAMPLE_LIMIT);
    }

    @Test
    void findFinding_plainSqlBranches_dispatchToMapper() {
        when(mapper.listLinkOrphan(SAMPLE_LIMIT)).thenReturn(Collections.emptyList());
        when(mapper.listRelationDuplicateAcrossCreator(SAMPLE_LIMIT)).thenReturn(Collections.emptyList());
        when(mapper.listRelationFamilyMismatch(SAMPLE_LIMIT)).thenReturn(Collections.emptyList());
        when(mapper.listAutoAccountOrphan(true, SAMPLE_LIMIT)).thenReturn(Collections.emptyList());
        when(mapper.listAttachedOrphan(SAMPLE_LIMIT)).thenReturn(Collections.emptyList());

        PlatformInspectionRepositoryImpl repository = repository();
        repository.findFinding(InspectionCheckEnum.LINK_ORPHAN, SAMPLE_LIMIT);
        repository.findFinding(InspectionCheckEnum.RELATION_DUPLICATE_ACROSS_CREATOR, SAMPLE_LIMIT);
        repository.findFinding(InspectionCheckEnum.RELATION_FAMILY_MISMATCH, SAMPLE_LIMIT);
        repository.findFinding(InspectionCheckEnum.AUTO_ACCOUNT_ORPHAN, SAMPLE_LIMIT);
        repository.findFinding(InspectionCheckEnum.ATTACHED_ORPHAN, SAMPLE_LIMIT);

        verify(mapper).listLinkOrphan(SAMPLE_LIMIT);
        verify(mapper).listRelationDuplicateAcrossCreator(SAMPLE_LIMIT);
        verify(mapper).listRelationFamilyMismatch(SAMPLE_LIMIT);
        verify(mapper).listAutoAccountOrphan(true, SAMPLE_LIMIT);
        verify(mapper).listAttachedOrphan(SAMPLE_LIMIT);
    }

    @Test
    void findFinding_lunarSolarMismatch_recalculatesFromSolarAndReportsDiff() {
        LocalDate solar = LocalDate.of(1986, 2, 9);
        NodeLunarRowDO consistent = lunarRow(1L, "蒙爷爷", solar, LunarSolarUtil.solarToLunar(solar));
        NodeLunarRowDO drifted = lunarRow(2L, "蒙奶奶", solar, "错年闰三月三十");
        when(mapper.listBirthDateNodes()).thenReturn(List.of(consistent, drifted));

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.LUNAR_SOLAR_MISMATCH, SAMPLE_LIMIT);

        assertEquals(1L, finding.getTotal());
        assertEquals(2L, finding.getSamples().get(0).getRefId());
        assertEquals("蒙奶奶", finding.getSamples().get(0).getSubject());
        assertTrue(finding.getSamples().get(0).getDetail().contains("错年闰三月三十"));
    }

    @Test
    void findFinding_settingUnknownKey_filtersWhitelistAndReportsBothScopes() {
        when(mapper.listSettingRows()).thenReturn(List.of(
                settingRow("FAMILY", 7L, 11L, FamilySettingKeyEnum.MAX_NODE_COUNT.getCode()),
                settingRow("FAMILY", 8L, 11L, "LEGACY_FLAG"),
                settingRow("PLATFORM", 9L, null, "OLD_SWITCH"),
                settingRow("PLATFORM", 10L, null, "REGISTER_ENABLED")));

        InspectionFinding finding = repository().findFinding(InspectionCheckEnum.SETTING_UNKNOWN_KEY, SAMPLE_LIMIT);

        assertEquals(2L, finding.getTotal());
        assertEquals("family#11", finding.getSamples().get(0).getSubject());
        assertEquals("平台配置", finding.getSamples().get(1).getSubject());
        assertTrue(finding.getSamples().get(0).getDetail().contains("LEGACY_FLAG"));
    }

    private InspectionSampleDO row(long total) {
        InspectionSampleDO row = new InspectionSampleDO();
        row.setRefId(REF_ID);
        row.setSubject(SUBJECT);
        row.setDetail(DETAIL);
        row.setTotal(total);
        return row;
    }

    private NodeLunarRowDO lunarRow(Long id, String name, LocalDate birthDate, String lunarBirthDate) {
        NodeLunarRowDO row = new NodeLunarRowDO();
        row.setId(id);
        row.setName(name);
        row.setBirthDate(birthDate);
        row.setLunarBirthDate(lunarBirthDate);
        return row;
    }

    private SettingRowDO settingRow(String scope, Long id, Long familyId, String settingKey) {
        SettingRowDO row = new SettingRowDO();
        row.setScope(scope);
        row.setId(id);
        row.setFamilyId(familyId);
        row.setSettingKey(settingKey);
        row.setSettingValue("1");
        return row;
    }
}
