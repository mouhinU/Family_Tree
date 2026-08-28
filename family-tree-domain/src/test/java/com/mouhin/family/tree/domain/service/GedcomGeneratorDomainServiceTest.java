package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GEDCOM 生成领域服务单元测试。
 * 覆盖：基本导出、夫妻关系、亲子关系、日期格式化、自定义标签。
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
class GedcomGeneratorDomainServiceTest {

    private final GedcomGeneratorDomainService generator = new GedcomGeneratorDomainService();

    @Test
    void generate_singleNode_containsHeaderAndIndividual() {
        FamilyNode node = createNode(1L, "张三", 1, "M");
        node.setBirthDate(LocalDate.of(1950, 1, 15));
        node.setDeathDate(LocalDate.of(2020, 12, 20));
        node.setRemark("家族创始人");

        String result = generator.generate(List.of(node), List.of(), "张氏家族");

        // 验证头部
        assertTrue(result.contains("0 HEAD"));
        assertTrue(result.contains("1 CHAR UTF-8"));
        assertTrue(result.contains("1 DEST 张氏家族"));

        // 验证个人记录
        assertTrue(result.contains("0 @I1@ INDI"));
        assertTrue(result.contains("1 NAME 张三"));
        assertTrue(result.contains("1 SEX M"));
        assertTrue(result.contains("2 DATE 15 JAN 1950"));
        assertTrue(result.contains("2 DATE 20 DEC 2020"));
        assertTrue(result.contains("1 NOTE 家族创始人"));

        // 验证尾部
        assertTrue(result.contains("0 TRLR"));
    }

    @Test
    void generate_spouseRelation_createsFamilyRecord() {
        FamilyNode husband = createNode(1L, "张三", 1, "M");
        FamilyNode wife = createNode(2L, "李四", 1, "F");

        FamilyRelation spouseRelation = new FamilyRelation();
        spouseRelation.setFromNodeId(1L);
        spouseRelation.setToNodeId(2L);
        spouseRelation.setRelationType(RelationTypeEnum.SPOUSE.getCode());
        spouseRelation.setMarriageDate(LocalDate.of(1975, 10, 10));

        String result = generator.generate(
                List.of(husband, wife), List.of(spouseRelation), "家族");

        assertTrue(result.contains("0 @F1@ FAM"));
        assertTrue(result.contains("1 HUSB @I1@"));
        assertTrue(result.contains("1 WIFE @I2@"));
        assertTrue(result.contains("1 MARR"));
        assertTrue(result.contains("2 DATE 10 OCT 1975"));
    }

    @Test
    void generate_parentChildRelation_childInFamilyRecord() {
        FamilyNode father = createNode(1L, "张三", 1, "M");
        FamilyNode mother = createNode(2L, "李四", 1, "F");
        FamilyNode child = createNode(3L, "张小明", 2, "M");

        FamilyRelation spouseRelation = new FamilyRelation();
        spouseRelation.setFromNodeId(1L);
        spouseRelation.setToNodeId(2L);
        spouseRelation.setRelationType(RelationTypeEnum.SPOUSE.getCode());

        FamilyRelation parentChildRelation = new FamilyRelation();
        parentChildRelation.setFromNodeId(1L);
        parentChildRelation.setToNodeId(3L);
        parentChildRelation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());

        String result = generator.generate(
                List.of(father, mother, child),
                List.of(spouseRelation, parentChildRelation),
                "家族");

        assertTrue(result.contains("1 CHIL @I3@"));
    }

    @Test
    void generate_customTags_ziHaoHui() {
        FamilyNode node = createNode(1L, "张大伟", 1, "M");
        node.setZi("子渊");
        node.setHao("清风居士");
        node.setHui("大伟");

        String result = generator.generate(List.of(node), List.of(), "家族");

        assertTrue(result.contains("1 _ZI 子渊"));
        assertTrue(result.contains("1 _HAO 清风居士"));
        assertTrue(result.contains("1 _HUI 大伟"));
    }

    @Test
    void generate_femaleGender_sexIsF() {
        FamilyNode node = createNode(1L, "李四", 1, "F");

        String result = generator.generate(List.of(node), List.of(), "家族");

        assertTrue(result.contains("1 SEX F"));
    }

    @Test
    void generate_unknownGender_sexIsU() {
        FamilyNode node = createNode(1L, "未知", 1, null);

        String result = generator.generate(List.of(node), List.of(), "家族");

        assertTrue(result.contains("1 SEX U"));
    }

    @Test
    void generate_emptyNodes_onlyHeaderAndTrailer() {
        String result = generator.generate(List.of(), List.of(), "家族");

        assertTrue(result.contains("0 HEAD"));
        assertTrue(result.contains("0 TRLR"));
        assertFalse(result.contains("INDI"));
        assertFalse(result.contains("FAM"));
    }

    private FamilyNode createNode(Long id, String name, int generation, String sex) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setUserId(1L);
        node.setFamilyId(100L);
        node.setName(name);
        node.setGeneration(generation);
        if ("M".equals(sex)) {
            node.setGender(1);
        } else if ("F".equals(sex)) {
            node.setGender(2);
        } else {
            node.setGender(0);
        }
        return node;
    }
}
