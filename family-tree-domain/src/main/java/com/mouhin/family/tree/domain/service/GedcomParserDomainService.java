package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.domain.gedcom.GedcomData;
import com.mouhin.family.tree.domain.gedcom.GedcomFamily;
import com.mouhin.family.tree.domain.gedcom.GedcomIndividual;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * GEDCOM 文件解析领域服务，将 GEDCOM 文本解析为中间数据结构
 * <p>
 * 支持 GEDCOM 5.5.1 标准格式，包括自定义标签 _ZI（字）、_HAO（号）、_HUI（讳）。
 * </p>
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Service
public class GedcomParserDomainService {

    private static final Logger logger = LoggerFactory.getLogger(GedcomParserDomainService.class);

    /**
     * 解析 GEDCOM 文本内容
     *
     * @param content GEDCOM 文件文本
     * @return 解析后的 GEDCOM 数据结构
     */
    public GedcomData parse(String content) {
        GedcomData data = new GedcomData();

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            List<GedcomLine> lines = readLines(reader);
            int i = 0;

            while (i < lines.size()) {
                GedcomLine line = lines.get(i);

                if (line.level == 0) {
                    if ("INDI".equals(line.tag)) {
                        GedcomIndividual individual = parseIndividual(lines, i);
                        data.getIndividuals().add(individual);
                        i = findNextLevel0(lines, i);
                    } else if ("FAM".equals(line.tag)) {
                        GedcomFamily family = parseFamily(lines, i);
                        data.getFamilies().add(family);
                        i = findNextLevel0(lines, i);
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
            }
        } catch (IOException e) {
            logger.error("Failed to parse GEDCOM content", e);
            throw new IllegalArgumentException("GEDCOM 文件解析失败: " + e.getMessage(), e);
        }

        logger.info("Parsed GEDCOM: {} individuals, {} families",
                data.getIndividuals().size(), data.getFamilies().size());
        return data;
    }

    /**
     * GEDCOM 行结构化表示
     * <p>
     * GEDCOM 行格式：
     * - Level 0 有 xref: {@code 0 @I1@ INDI}
     * - Level 0 无 xref: {@code 0 HEAD}
     * - Level 1+: {@code 1 NAME 张 /三/}
     * </p>
     */
    private record GedcomLine(int level, String xref, String tag, String value) {
    }

    /**
     * 读取所有行并解析为结构化表示
     */
    private List<GedcomLine> readLines(BufferedReader reader) throws IOException {
        List<GedcomLine> lines = new ArrayList<>();
        String raw;
        while ((raw = reader.readLine()) != null) {
            raw = raw.trim();
            if (raw.isEmpty()) {
                continue;
            }
            GedcomLine gl = parseLine(raw);
            if (gl != null) {
                lines.add(gl);
            }
        }
        return lines;
    }

    /**
     * 解析单行 GEDCOM 文本为结构化对象
     * <p>
     * Level 0: "0 @xref@ TAG" 或 "0 TAG" 或 "0 TAG value"
     * Level 1+: "level TAG" 或 "level TAG value"
     * </p>
     */
    private GedcomLine parseLine(String raw) {
        String[] parts = raw.split("\\s+", 3);
        if (parts.length < 2) {
            return null;
        }

        int level;
        try {
            level = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return null;
        }

        if (level == 0) {
            // Level 0: 可能有 xref
            if (parts[1].startsWith("@")) {
                // 格式: 0 @xref@ TAG [value]
                String xref = parts[1];
                if (parts.length >= 3) {
                    String rest = parts[2];
                    int spaceIdx = rest.indexOf(' ');
                    if (spaceIdx > 0) {
                        return new GedcomLine(0, xref,
                                rest.substring(0, spaceIdx), rest.substring(spaceIdx + 1).trim());
                    }
                    return new GedcomLine(0, xref, rest, "");
                }
                return null;
            } else {
                // 格式: 0 TAG [value]
                String tag = parts[1];
                String value = parts.length >= 3 ? parts[2].trim() : "";
                return new GedcomLine(0, null, tag, value);
            }
        } else {
            // Level 1+: 格式为 level TAG [value]
            String tag = parts[1];
            String value = parts.length >= 3 ? parts[2].trim() : "";
            return new GedcomLine(level, null, tag, value);
        }
    }

    /**
     * 查找下一个 level 0 记录的位置
     */
    private int findNextLevel0(List<GedcomLine> lines, int current) {
        int i = current + 1;
        while (i < lines.size()) {
            if (lines.get(i).level == 0) {
                return i;
            }
            i++;
        }
        return lines.size();
    }

    /**
     * 解析 INDI 记录
     */
    private GedcomIndividual parseIndividual(List<GedcomLine> lines, int start) {
        GedcomIndividual individual = new GedcomIndividual();
        GedcomLine firstLine = lines.get(start);

        individual.setXref(firstLine.xref);

        int end = findNextLevel0(lines, start);
        for (int i = start + 1; i < end; i++) {
            GedcomLine line = lines.get(i);
            if (line.level != 1) {
                continue;
            }

            switch (line.tag) {
                case "NAME" -> individual.setName(cleanGedcomValue(line.value));
                case "SEX" -> individual.setSex(line.value.trim());
                case "BIRT" -> individual.setBirthDate(
                        findSubTagValue(lines, i + 1, end, "DATE"));
                case "DEAT" -> individual.setDeathDate(
                        findSubTagValue(lines, i + 1, end, "DATE"));
                case "NOTE" -> individual.setNote(cleanGedcomValue(line.value));
                case "_ZI" -> individual.setZi(cleanGedcomValue(line.value));
                case "_HAO" -> individual.setHao(cleanGedcomValue(line.value));
                case "_HUI" -> individual.setHui(cleanGedcomValue(line.value));
                default -> { /* 忽略未识别标签 */ }
            }
        }

        return individual;
    }

    /**
     * 解析 FAM 记录
     */
    private GedcomFamily parseFamily(List<GedcomLine> lines, int start) {
        GedcomFamily family = new GedcomFamily();
        GedcomLine firstLine = lines.get(start);

        family.setXref(firstLine.xref);

        int end = findNextLevel0(lines, start);
        for (int i = start + 1; i < end; i++) {
            GedcomLine line = lines.get(i);
            if (line.level != 1) {
                continue;
            }

            switch (line.tag) {
                case "HUSB" -> family.setHusbXref(line.value.trim());
                case "WIFE" -> family.setWifeXref(line.value.trim());
                case "CHIL" -> family.getChildrenXrefs().add(line.value.trim());
                case "MARR" -> family.setMarriageDate(
                        findSubTagValue(lines, i + 1, end, "DATE"));
                case "DIV" -> family.setDivorceDate(
                        findSubTagValue(lines, i + 1, end, "DATE"));
                default -> { /* 忽略未识别标签 */ }
            }
        }

        return family;
    }

    /**
     * 在子行中查找指定标签的值
     *
     * @param lines     所有行
     * @param from      起始索引
     * @param to        结束索引（不含）
     * @param targetTag 目标标签名
     * @return 标签值，未找到返回 null
     */
    private String findSubTagValue(List<GedcomLine> lines, int from, int to,
                                   String targetTag) {
        for (int i = from; i < to; i++) {
            GedcomLine line = lines.get(i);
            if (line.level == 0) {
                break;
            }
            if (targetTag.equals(line.tag)) {
                return line.value.trim();
            }
        }
        return null;
    }

    /**
     * 清理 GEDCOM 值（去除斜杠包裹的姓氏标记等）
     *
     * @param value 原始值
     * @return 清理后的值
     */
    private String cleanGedcomValue(String value) {
        if (value == null) {
            return null;
        }
        // GEDCOM 姓名格式: Given /Surname/ → 去除斜杠
        String cleaned = value.replace("/", "").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
