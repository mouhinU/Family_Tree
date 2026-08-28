package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.gedcom.GedcomData;
import com.mouhin.family.tree.domain.gedcom.GedcomFamily;
import com.mouhin.family.tree.domain.gedcom.GedcomIndividual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GEDCOM 文件生成领域服务，将族谱数据导出为 GEDCOM 5.5.1 格式文本
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Service
public class GedcomGeneratorDomainService {

    private static final Logger logger = LoggerFactory.getLogger(GedcomGeneratorDomainService.class);

    private static final DateTimeFormatter GEDCOM_DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    /**
     * 将族谱节点和关系数据生成 GEDCOM 格式文本
     *
     * @param nodes     族谱节点列表
     * @param relations 族谱关系列表
     * @param familyName 家族名称（用于头部信息）
     * @return GEDCOM 格式文本
     */
    public String generate(List<FamilyNode> nodes, List<FamilyRelation> relations,
                           String familyName) {
        StringBuilder sb = new StringBuilder();

        // 头部
        writeHeader(sb, familyName, nodes.size());

        // 个人记录
        Map<Long, String> nodeIdToXref = new HashMap<>(nodes.size());
        for (int i = 0; i < nodes.size(); i++) {
            FamilyNode node = nodes.get(i);
            String xref = "@I" + (i + 1) + "@";
            nodeIdToXref.put(node.getId(), xref);
            writeIndividual(sb, xref, node);
        }

        // 家庭记录：按夫妻关系分组
        Map<String, GedcomFamily> familyMap = buildFamilyMap(nodes, relations, nodeIdToXref);
        int famIndex = 1;
        for (GedcomFamily family : familyMap.values()) {
            writeFamily(sb, "@F" + famIndex + "@", family);
            famIndex++;
        }

        // 尾部
        sb.append("0 TRLR\n");

        logger.info("Generated GEDCOM: {} individuals, {} families",
                nodes.size(), familyMap.size());
        return sb.toString();
    }

    /**
     * 写入 GEDCOM 头部
     */
    private void writeHeader(StringBuilder sb, String familyName, int nodeCount) {
        sb.append("0 HEAD\n");
        sb.append("1 SOUR Family Tree System\n");
        sb.append("2 VERS 1.0\n");
        sb.append("1 GEDC\n");
        sb.append("2 VERS 5.5.1\n");
        sb.append("2 FORM LINEAGE-LINKED\n");
        sb.append("1 CHAR UTF-8\n");
        if (familyName != null) {
            sb.append("1 DEST ").append(escapeGedcomValue(familyName)).append('\n');
        }
        sb.append("1 NOTE 导出节点数: ").append(nodeCount).append('\n');
    }

    /**
     * 写入个人记录
     */
    private void writeIndividual(StringBuilder sb, String xref, FamilyNode node) {
        sb.append("0 ").append(xref).append(" INDI\n");

        if (node.getName() != null) {
            sb.append("1 NAME ").append(escapeGedcomValue(node.getName())).append('\n');
        }

        if (node.getGender() != null) {
            String sex = switch (node.getGender()) {
                case 1 -> "M";
                case 2 -> "F";
                default -> "U";
            };
            sb.append("1 SEX ").append(sex).append('\n');
        }

        if (node.getBirthDate() != null) {
            sb.append("1 BIRT\n");
            sb.append("2 DATE ").append(formatGedcomDate(node.getBirthDate())).append('\n');
        }

        if (node.getDeathDate() != null) {
            sb.append("1 DEAT\n");
            sb.append("2 DATE ").append(formatGedcomDate(node.getDeathDate())).append('\n');
        }

        // 传统族谱字段（自定义标签）
        if (node.getZi() != null) {
            sb.append("1 _ZI ").append(escapeGedcomValue(node.getZi())).append('\n');
        }
        if (node.getHao() != null) {
            sb.append("1 _HAO ").append(escapeGedcomValue(node.getHao())).append('\n');
        }
        if (node.getHui() != null) {
            sb.append("1 _HUI ").append(escapeGedcomValue(node.getHui())).append('\n');
        }

        if (node.getRemark() != null) {
            sb.append("1 NOTE ").append(escapeGedcomValue(node.getRemark())).append('\n');
        }
    }

    /**
     * 按夫妻关系构建家庭记录映射
     *
     * @param nodes        所有节点
     * @param relations    所有关系
     * @param nodeIdToXref 节点ID到GEDCOM引用的映射
     * @return 夫妻对标识 → GedcomFamily 的映射
     */
    private Map<String, GedcomFamily> buildFamilyMap(List<FamilyNode> nodes,
                                                     List<FamilyRelation> relations,
                                                     Map<Long, String> nodeIdToXref) {
        Map<String, GedcomFamily> familyMap = new HashMap<>();

        // 为每个夫妻关系创建 FAM 记录
        for (FamilyRelation relation : relations) {
            if (relation.getRelationType() != RelationTypeEnum.SPOUSE.getCode()) {
                continue;
            }
            String husbXref = nodeIdToXref.get(relation.getFromNodeId());
            String wifeXref = nodeIdToXref.get(relation.getToNodeId());

            // 确定丈夫和妻子（根据性别）
            FamilyNode fromNode = findNodeById(nodes, relation.getFromNodeId());
            FamilyNode toNode = findNodeById(nodes, relation.getToNodeId());
            if (fromNode != null && fromNode.getGender() != null
                    && fromNode.getGender() == 2) {
                // fromNode 是女性，交换
                String temp = husbXref;
                husbXref = wifeXref;
                wifeXref = temp;
            }

            String key = husbXref + "-" + wifeXref;
            GedcomFamily family = familyMap.computeIfAbsent(key, k -> new GedcomFamily());
            family.setHusbXref(husbXref);
            family.setWifeXref(wifeXref);

            if (relation.getMarriageDate() != null) {
                family.setMarriageDate(formatGedcomDate(relation.getMarriageDate()));
            }
            if (relation.getDivorceDate() != null) {
                family.setDivorceDate(formatGedcomDate(relation.getDivorceDate()));
            }
        }

        // 为每个亲子关系分配子女到对应家庭
        for (FamilyRelation relation : relations) {
            if (relation.getRelationType() != RelationTypeEnum.PARENT_CHILD.getCode()) {
                continue;
            }
            Long parentId = relation.getFromNodeId();
            Long childId = relation.getToNodeId();
            String childXref = nodeIdToXref.get(childId);
            if (childXref == null) {
                continue;
            }

            // 查找父/母的配偶关系，确定所属家庭
            String parentXref = nodeIdToXref.get(parentId);
            for (GedcomFamily family : familyMap.values()) {
                if (parentXref.equals(family.getHusbXref())
                        || parentXref.equals(family.getWifeXref())) {
                    if (!family.getChildrenXrefs().contains(childXref)) {
                        family.getChildrenXrefs().add(childXref);
                    }
                }
            }
        }

        return familyMap;
    }

    /**
     * 写入家庭记录
     */
    private void writeFamily(StringBuilder sb, String xref, GedcomFamily family) {
        sb.append("0 ").append(xref).append(" FAM\n");

        if (family.getHusbXref() != null) {
            sb.append("1 HUSB ").append(family.getHusbXref()).append('\n');
        }
        if (family.getWifeXref() != null) {
            sb.append("1 WIFE ").append(family.getWifeXref()).append('\n');
        }
        for (String childXref : family.getChildrenXrefs()) {
            sb.append("1 CHIL ").append(childXref).append('\n');
        }
        if (family.getMarriageDate() != null) {
            sb.append("1 MARR\n");
            sb.append("2 DATE ").append(family.getMarriageDate()).append('\n');
        }
        if (family.getDivorceDate() != null) {
            sb.append("1 DIV\n");
            sb.append("2 DATE ").append(family.getDivorceDate()).append('\n');
        }
    }

    /**
     * 将 LocalDate 格式化为 GEDCOM 日期格式（如 15 JAN 2000）
     *
     * @param date 日期
     * @return GEDCOM 格式日期字符串
     */
    private String formatGedcomDate(LocalDate date) {
        return date.format(GEDCOM_DATE_FORMAT).toUpperCase(Locale.ENGLISH);
    }

    /**
     * 转义 GEDCOM 值中的特殊字符
     */
    private String escapeGedcomValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\n", " ").replace("\r", "");
    }

    /**
     * 根据ID查找节点
     */
    private FamilyNode findNodeById(List<FamilyNode> nodes, Long nodeId) {
        for (FamilyNode node : nodes) {
            if (nodeId.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }
}
