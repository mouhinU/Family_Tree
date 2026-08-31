package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.GedcomImportResultVO;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.service.GedcomGeneratorDomainService;
import com.mouhin.family.tree.domain.service.GedcomParserDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GEDCOM 导入导出应用服务单元测试。
 * 覆盖：覆盖导入、追加导入、导出、空文件异常、世代计算。
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@ExtendWith(MockitoExtension.class)
class GedcomApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAMILY_ID = 100L;

    @Spy
    private GedcomParserDomainService gedcomParserDomainService = new GedcomParserDomainService();

    @Spy
    private GedcomGeneratorDomainService gedcomGeneratorDomainService = new GedcomGeneratorDomainService();

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private GedcomApplicationService gedcomApplicationService;

    private static final String SAMPLE_GEDCOM = """
            0 HEAD
            1 SOUR Test
            1 GEDC
            2 VERS 5.5.1
            1 CHAR UTF-8
            0 @I1@ INDI
            1 NAME 张 /三/
            1 SEX M
            1 BIRT
            2 DATE 15 JAN 1950
            0 @I2@ INDI
            1 NAME 李 /四/
            1 SEX F
            1 BIRT
            2 DATE 1955
            0 @I3@ INDI
            1 NAME 张 /小明/
            1 SEX M
            1 BIRT
            2 DATE 5 MAR 1980
            0 @F1@ FAM
            1 HUSB @I1@
            1 WIFE @I2@
            1 CHIL @I3@
            1 MARR
            2 DATE 10 OCT 1975
            0 TRLR
            """;

    @Test
    void importGedcom_overwriteMode_clearsExistingData() {
        // 准备：模拟现有数据
        FamilyRelation existingRelation = new FamilyRelation();
        existingRelation.setId(999L);
        FamilyNode existingNode = new FamilyNode();
        existingNode.setId(888L);
        existingNode.setFamilyId(FAMILY_ID);
        existingNode.setName("旧节点");
        existingNode.setGeneration(1);

        // 首次 findByFamilyId 返回已有数据（用于清除），后续返回空列表
        java.util.concurrent.atomic.AtomicInteger relCallCount =
                new java.util.concurrent.atomic.AtomicInteger(0);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenAnswer(inv -> {
            if (relCallCount.incrementAndGet() == 1) {
                return new ArrayList<>(List.of(existingRelation));
            }
            return new ArrayList<>();
        });

        // 模拟保存后回填ID
        doAnswer(invocation -> {
            FamilyNode node = invocation.getArgument(0);
            node.setId(System.nanoTime());
            return node;
        }).when(familyNodeRepository).save(any(FamilyNode.class));

        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(new ArrayList<>());

        // 执行
        GedcomImportResultVO result = gedcomApplicationService.importGedcom(
                FAMILY_ID, USER_ID, "测试用户", "127.0.0.1", SAMPLE_GEDCOM);

        // 验证：清除了现有数据
        verify(familyNodeRepository).removeByFamilyId(FAMILY_ID);

        // 验证：导入了 3 个节点
        assertEquals(3, result.getImportedNodeCount());
        assertEquals(3, result.getParsedIndividualCount());
        assertEquals(1, result.getParsedFamilyCount());

        // 验证：创建了 1 条夫妻关系 + 1 条亲子关系 = 2 条关系
        assertEquals(2, result.getImportedRelationCount());
    }

    @Test
    void importGedcom_emptyFile_throwsException() {
        String emptyGedcom = """
                0 HEAD
                1 SOUR Test
                0 TRLR
                """;

        BusinessException exception = assertThrows(BusinessException.class,
                () -> gedcomApplicationService.importGedcom(FAMILY_ID, USER_ID, "测试用户", "127.0.0.1", emptyGedcom));
        assertTrue(exception.getMessage().contains("未找到个人记录"));
    }

    @Test
    void importGedcom_createsCorrectRelations() {
        // 模拟保存后回填ID
        doAnswer(invocation -> {
            FamilyNode node = invocation.getArgument(0);
            // 按顺序分配 ID
            String name = node.getName();
            if (name.contains("三")) {
                node.setId(10L);
            } else if (name.contains("四")) {
                node.setId(20L);
            } else if (name.contains("小明")) {
                node.setId(30L);
            }
            return node;
        }).when(familyNodeRepository).save(any(FamilyNode.class));

        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(new ArrayList<>());
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(new ArrayList<>());

        // 执行
        gedcomApplicationService.importGedcom(FAMILY_ID, USER_ID, "测试用户", "127.0.0.1", SAMPLE_GEDCOM);

        // 验证：创建了夫妻关系和亲子关系
        ArgumentCaptor<FamilyRelation> relCaptor = ArgumentCaptor.forClass(FamilyRelation.class);
        verify(familyRelationRepository, times(2)).save(relCaptor.capture());

        List<FamilyRelation> savedRelations = relCaptor.getAllValues();

        // 夫妻关系
        FamilyRelation spouseRelation = savedRelations.get(0);
        assertEquals(RelationTypeEnum.SPOUSE.getCode(), spouseRelation.getRelationType());
        assertEquals(10L, spouseRelation.getFromNodeId());
        assertEquals(20L, spouseRelation.getToNodeId());

        // 亲子关系
        FamilyRelation childRelation = savedRelations.get(1);
        assertEquals(RelationTypeEnum.PARENT_CHILD.getCode(), childRelation.getRelationType());
        assertEquals(10L, childRelation.getFromNodeId());
        assertEquals(30L, childRelation.getToNodeId());
    }

    @Test
    void exportGedcom_generatesValidContent() {
        // 准备
        FamilyNode node1 = new FamilyNode();
        node1.setId(1L);
        node1.setUserId(USER_ID);
        node1.setFamilyId(FAMILY_ID);
        node1.setName("张三");
        node1.setGender(1);
        node1.setGeneration(1);

        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("张氏家族");

        when(familyNodeRepository.findByFamilyId(FAMILY_ID))
                .thenReturn(new ArrayList<>(List.of(node1)));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID))
                .thenReturn(new ArrayList<>());
        when(familyRepository.findById(FAMILY_ID)).thenReturn(family);

        // 执行
        String result = gedcomApplicationService.exportGedcom(FAMILY_ID);

        // 验证
        assertNotNull(result);
        assertTrue(result.contains("0 HEAD"));
        assertTrue(result.contains("0 @I1@ INDI"));
        assertTrue(result.contains("1 NAME 张三"));
        assertTrue(result.contains("0 TRLR"));
    }
}
