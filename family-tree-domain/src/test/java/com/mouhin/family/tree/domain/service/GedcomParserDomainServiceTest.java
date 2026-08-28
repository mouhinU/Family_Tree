package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.domain.gedcom.GedcomData;
import com.mouhin.family.tree.domain.gedcom.GedcomFamily;
import com.mouhin.family.tree.domain.gedcom.GedcomIndividual;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GEDCOM 解析领域服务单元测试。
 * 覆盖：标准 GEDCOM 解析、自定义标签解析、空文件、多种日期格式。
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
class GedcomParserDomainServiceTest {

    private final GedcomParserDomainService parser = new GedcomParserDomainService();

    @Test
    void parse_standardGedcom_extractsIndividualsAndFamilies() {
        String content = """
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
                1 DEAT
                2 DATE 20 DEC 2020
                1 NOTE 家族创始人
                0 @I2@ INDI
                1 NAME 李 /四/
                1 SEX F
                1 BIRT
                2 DATE 1955
                0 @F1@ FAM
                1 HUSB @I1@
                1 WIFE @I2@
                1 CHIL @I3@
                1 MARR
                2 DATE 10 OCT 1975
                0 @I3@ INDI
                1 NAME 张 /小明/
                1 SEX M
                1 BIRT
                2 DATE 5 MAR 1980
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        // 验证个人记录
        assertEquals(3, data.getIndividuals().size());

        GedcomIndividual i1 = data.getIndividuals().get(0);
        assertEquals("@I1@", i1.getXref());
        assertEquals("张 三", i1.getName());
        assertEquals("M", i1.getSex());
        assertEquals("15 JAN 1950", i1.getBirthDate());
        assertEquals("20 DEC 2020", i1.getDeathDate());
        assertEquals("家族创始人", i1.getNote());

        GedcomIndividual i2 = data.getIndividuals().get(1);
        assertEquals("@I2@", i2.getXref());
        assertEquals("李 四", i2.getName());
        assertEquals("F", i2.getSex());
        assertEquals("1955", i2.getBirthDate());
        assertNull(i2.getDeathDate());

        GedcomIndividual i3 = data.getIndividuals().get(2);
        assertEquals("@I3@", i3.getXref());
        assertEquals("张 小明", i3.getName());

        // 验证家庭记录
        assertEquals(1, data.getFamilies().size());
        GedcomFamily f1 = data.getFamilies().get(0);
        assertEquals("@F1@", f1.getXref());
        assertEquals("@I1@", f1.getHusbXref());
        assertEquals("@I2@", f1.getWifeXref());
        assertEquals(1, f1.getChildrenXrefs().size());
        assertEquals("@I3@", f1.getChildrenXrefs().get(0));
        assertEquals("10 OCT 1975", f1.getMarriageDate());
    }

    @Test
    void parse_customTags_extractsZiHaoHui() {
        String content = """
                0 HEAD
                1 SOUR Test
                0 @I1@ INDI
                1 NAME 张 /大伟/
                1 SEX M
                1 _ZI 子渊
                1 _HAO 清风居士
                1 _HUI 大伟
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        assertEquals(1, data.getIndividuals().size());
        GedcomIndividual individual = data.getIndividuals().get(0);
        assertEquals("子渊", individual.getZi());
        assertEquals("清风居士", individual.getHao());
        assertEquals("大伟", individual.getHui());
    }

    @Test
    void parse_emptyContent_returnsEmptyData() {
        String content = """
                0 HEAD
                1 SOUR Test
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        assertTrue(data.getIndividuals().isEmpty());
        assertTrue(data.getFamilies().isEmpty());
    }

    @Test
    void parse_multipleFamilies_allExtracted() {
        String content = """
                0 HEAD
                1 SOUR Test
                0 @I1@ INDI
                1 NAME 父 /一/
                1 SEX M
                0 @I2@ INDI
                1 NAME 母 /一/
                1 SEX F
                0 @I3@ INDI
                1 NAME 子 /一/
                1 SEX M
                0 @I4@ INDI
                1 NAME 媳 /一/
                1 SEX F
                0 @I5@ INDI
                1 NAME 孙 /一/
                1 SEX M
                0 @F1@ FAM
                1 HUSB @I1@
                1 WIFE @I2@
                1 CHIL @I3@
                0 @F2@ FAM
                1 HUSB @I3@
                1 WIFE @I4@
                1 CHIL @I5@
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        assertEquals(5, data.getIndividuals().size());
        assertEquals(2, data.getFamilies().size());

        GedcomFamily f1 = data.getFamilies().get(0);
        assertEquals("@I1@", f1.getHusbXref());
        assertEquals("@I2@", f1.getWifeXref());
        assertEquals(1, f1.getChildrenXrefs().size());

        GedcomFamily f2 = data.getFamilies().get(1);
        assertEquals("@I3@", f2.getHusbXref());
        assertEquals("@I4@", f2.getWifeXref());
        assertEquals("@I5@", f2.getChildrenXrefs().get(0));
    }

    @Test
    void parse_divorceDate_extracted() {
        String content = """
                0 HEAD
                1 SOUR Test
                0 @I1@ INDI
                1 NAME 张 /三/
                1 SEX M
                0 @I2@ INDI
                1 NAME 李 /四/
                1 SEX F
                0 @F1@ FAM
                1 HUSB @I1@
                1 WIFE @I2@
                1 MARR
                2 DATE 1970
                1 DIV
                2 DATE 1985
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        assertEquals(1, data.getFamilies().size());
        GedcomFamily family = data.getFamilies().get(0);
        assertEquals("1970", family.getMarriageDate());
        assertEquals("1985", family.getDivorceDate());
    }

    @Test
    void parse_individualWithoutXref_stillParsed() {
        String content = """
                0 HEAD
                1 SOUR Test
                0 INDI
                1 NAME 无引用 /人/
                1 SEX M
                0 TRLR
                """;

        GedcomData data = parser.parse(content);

        assertEquals(1, data.getIndividuals().size());
        assertNull(data.getIndividuals().get(0).getXref());
        assertEquals("无引用 人", data.getIndividuals().get(0).getName());
    }
}
