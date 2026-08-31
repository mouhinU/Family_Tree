package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.GedcomImportResultVO;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.event.GedcomImportedEvent;
import com.mouhin.family.tree.domain.gedcom.GedcomData;
import com.mouhin.family.tree.domain.gedcom.GedcomFamily;
import com.mouhin.family.tree.domain.gedcom.GedcomIndividual;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.service.GedcomGeneratorDomainService;
import com.mouhin.family.tree.domain.service.GedcomParserDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * GEDCOM 导入导出应用服务
 * <p>
 * 负责编排 GEDCOM 文件的解析、数据转换、持久化和生成流程。
 * 导入模式支持覆盖导入（清空现有数据）和追加导入（保留现有数据）。
 * </p>
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@Service
public class GedcomApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(GedcomApplicationService.class);

    private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    /** GEDCOM 月份缩写 → 数字映射 */
    private static final Map<String, Integer> GEDCOM_MONTH_MAP = buildMonthMap();

    private final GedcomParserDomainService gedcomParserDomainService;
    private final GedcomGeneratorDomainService gedcomGeneratorDomainService;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyRepository familyRepository;
    private final ApplicationEventPublisher eventPublisher;

    public GedcomApplicationService(GedcomParserDomainService gedcomParserDomainService,
                                    GedcomGeneratorDomainService gedcomGeneratorDomainService,
                                    FamilyNodeRepository familyNodeRepository,
                                    FamilyRelationRepository familyRelationRepository,
                                    FamilyRepository familyRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.gedcomParserDomainService = gedcomParserDomainService;
        this.gedcomGeneratorDomainService = gedcomGeneratorDomainService;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyRepository = familyRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 导入 GEDCOM 文件（覆盖模式：清空现有数据后导入）
     *
     * @param familyId  目标家族ID
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param ipAddress 客户端IP
     * @param content   GEDCOM 文件文本内容
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public GedcomImportResultVO importGedcom(Long familyId, Long userId, String username,
                                             String ipAddress, String content) {
        return doImport(familyId, userId, username, ipAddress, content, true);
    }

    /**
     * 追加导入 GEDCOM 文件（保留现有数据）
     *
     * @param familyId  目标家族ID
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param ipAddress 客户端IP
     * @param content   GEDCOM 文件文本内容
     * @return 导入结果
     */
    @Transactional(rollbackFor = Exception.class)
    public GedcomImportResultVO appendImportGedcom(Long familyId, Long userId, String username,
                                                   String ipAddress, String content) {
        return doImport(familyId, userId, username, ipAddress, content, false);
    }

    /**
     * 导出族谱数据为 GEDCOM 格式
     *
     * @param familyId 家族ID
     * @return GEDCOM 格式文本
     */
    public String exportGedcom(Long familyId) {
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> relations = familyRelationRepository.findByFamilyId(familyId);

        Family family = familyRepository.findById(familyId);
        String familyName = family != null ? family.getName() : "Family";

        logger.info("Exporting GEDCOM for family={}, nodes={}, relations={}",
                familyId, nodes.size(), relations.size());
        return gedcomGeneratorDomainService.generate(nodes, relations, familyName);
    }

    /**
     * 预览 GEDCOM 文件内容（不执行导入）
     *
     * @param content GEDCOM 文件文本内容
     * @return 解析后的数据结构
     */
    public GedcomData previewGedcom(String content) {
        return gedcomParserDomainService.parse(content);
    }

    /**
     * 执行导入逻辑
     *
     * @param familyId  目标家族ID
     * @param userId    操作用户ID
     * @param username  操作用户名
     * @param ipAddress 客户端IP
     * @param content   GEDCOM 文件内容
     * @param overwrite 是否覆盖现有数据
     * @return 导入结果
     */
    private GedcomImportResultVO doImport(Long familyId, Long userId, String username,
                                          String ipAddress, String content, boolean overwrite) {
        GedcomData data = gedcomParserDomainService.parse(content);

        if (data.getIndividuals().isEmpty()) {
            throw new BusinessException("GEDCOM 文件中未找到个人记录");
        }

        // 覆盖模式：清空现有数据
        if (overwrite) {
            familyRelationRepository.findByFamilyId(familyId)
                    .forEach(r -> familyRelationRepository.removeById(r.getId()));
            familyNodeRepository.removeByFamilyId(familyId);
            logger.info("Cleared existing data for family={} before import", familyId);
        }

        // 创建节点
        Map<String, Long> xrefToNodeId = new HashMap<>(data.getIndividuals().size());
        for (GedcomIndividual individual : data.getIndividuals()) {
            FamilyNode node = convertToFamilyNode(individual, familyId, userId);
            familyNodeRepository.save(node);
            xrefToNodeId.put(individual.getXref(), node.getId());
        }

        // 创建关系
        int relationCount = createRelationsFromGedcom(data, familyId, userId, xrefToNodeId);

        // 计算世代层级
        calculateAndSetGenerations(familyId);

        // 发布事件（树结构变更 + 导入操作日志）
        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
        eventPublisher.publishEvent(GedcomImportedEvent.of(familyId, xrefToNodeId.size(),
                relationCount, overwrite, userId, username, ipAddress));

        // 构建结果
        GedcomImportResultVO result = new GedcomImportResultVO();
        result.setImportedNodeCount(xrefToNodeId.size());
        result.setImportedRelationCount(relationCount);
        result.setParsedIndividualCount(data.getIndividuals().size());
        result.setParsedFamilyCount(data.getFamilies().size());
        result.setMessage(String.format("成功导入 %d 个节点、%d 条关系",
                xrefToNodeId.size(), relationCount));

        logger.info("GEDCOM import completed for family={}: {} nodes, {} relations",
                familyId, xrefToNodeId.size(), relationCount);
        return result;
    }

    /**
     * 将 GEDCOM 个人记录转换为 FamilyNode 领域实体
     */
    private FamilyNode convertToFamilyNode(GedcomIndividual individual, Long familyId, Long userId) {
        FamilyNode node = new FamilyNode();
        node.setUserId(userId);
        node.setFamilyId(familyId);
        node.setName(individual.getName() != null ? individual.getName().trim() : "未知");
        node.setGender(convertGedcomSex(individual.getSex()));
        node.setBirthDate(parseGedcomDate(individual.getBirthDate()));
        node.setDeathDate(parseGedcomDate(individual.getDeathDate()));
        node.setRemark(individual.getNote());
        node.setZi(individual.getZi());
        node.setHao(individual.getHao());
        node.setHui(individual.getHui());
        node.setGeneration(FamilyTreeConsts.DEFAULT_GENERATION);
        node.setCreateTime(LocalDateTime.now());
        node.setUpdateTime(LocalDateTime.now());
        return node;
    }

    /**
     * 从 GEDCOM 家庭记录创建关系
     *
     * @return 创建的关系数量
     */
    private int createRelationsFromGedcom(GedcomData data, Long familyId, Long userId,
                                          Map<String, Long> xrefToNodeId) {
        int count = 0;
        for (GedcomFamily family : data.getFamilies()) {
            Long husbId = xrefToNodeId.get(family.getHusbXref());
            Long wifeId = xrefToNodeId.get(family.getWifeXref());

            // 创建夫妻关系
            if (husbId != null && wifeId != null) {
                FamilyRelation spouseRelation = new FamilyRelation();
                spouseRelation.setUserId(userId);
                spouseRelation.setFamilyId(familyId);
                spouseRelation.setFromNodeId(husbId);
                spouseRelation.setToNodeId(wifeId);
                spouseRelation.setRelationType(RelationTypeEnum.SPOUSE.getCode());
                spouseRelation.setMarriageDate(parseGedcomDate(family.getMarriageDate()));
                spouseRelation.setDivorceDate(parseGedcomDate(family.getDivorceDate()));
                if (family.getDivorceDate() != null) {
                    spouseRelation.setDivorced(true);
                }
                spouseRelation.setCreateTime(LocalDateTime.now());
                spouseRelation.setUpdateTime(LocalDateTime.now());
                familyRelationRepository.save(spouseRelation);
                count++;
            }

            // 创建亲子关系
            Long parentId = husbId != null ? husbId : wifeId;
            if (parentId != null) {
                for (String childXref : family.getChildrenXrefs()) {
                    Long childId = xrefToNodeId.get(childXref);
                    if (childId != null) {
                        FamilyRelation childRelation = new FamilyRelation();
                        childRelation.setUserId(userId);
                        childRelation.setFamilyId(familyId);
                        childRelation.setFromNodeId(parentId);
                        childRelation.setToNodeId(childId);
                        childRelation.setRelationType(RelationTypeEnum.PARENT_CHILD.getCode());
                        childRelation.setCreateTime(LocalDateTime.now());
                        childRelation.setUpdateTime(LocalDateTime.now());
                        familyRelationRepository.save(childRelation);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 计算并设置家族中所有节点的世代层级
     * <p>
     * 算法：
     * 1. 找到所有根节点（无父节点）
     * 2. BFS 向下传播世代
     * 3. 配偶同步世代
     * 4. 未连接节点默认为第 1 世
     * </p>
     */
    private void calculateAndSetGenerations(Long familyId) {
        List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);

        if (allNodes.isEmpty()) {
            return;
        }

        // 构建邻接结构
        Map<Long, List<Long>> parentToChildren = new HashMap<>();
        Map<Long, Set<Long>> childToParents = new HashMap<>();
        Map<Long, Set<Long>> spouseMap = new HashMap<>();

        for (FamilyRelation relation : allRelations) {
            if (relation.isParentChild()) {
                parentToChildren.computeIfAbsent(relation.getFromNodeId(), k -> new ArrayList<>())
                        .add(relation.getToNodeId());
                childToParents.computeIfAbsent(relation.getToNodeId(), k -> new HashSet<>())
                        .add(relation.getFromNodeId());
            } else if (relation.isSpouse()) {
                spouseMap.computeIfAbsent(relation.getFromNodeId(), k -> new HashSet<>())
                        .add(relation.getToNodeId());
                spouseMap.computeIfAbsent(relation.getToNodeId(), k -> new HashSet<>())
                        .add(relation.getFromNodeId());
            }
        }

        // 节点映射
        Map<Long, FamilyNode> nodeMap = new HashMap<>(allNodes.size());
        for (FamilyNode node : allNodes) {
            nodeMap.put(node.getId(), node);
        }

        // 找到根节点（没有父节点的节点）
        Set<Long> roots = new HashSet<>();
        for (FamilyNode node : allNodes) {
            if (!childToParents.containsKey(node.getId())) {
                roots.add(node.getId());
            }
        }

        // BFS 分配世代
        Map<Long, Integer> generationMap = new HashMap<>();
        Deque<Long> queue = new ArrayDeque<>();
        for (Long root : roots) {
            generationMap.put(root, FamilyTreeConsts.DEFAULT_GENERATION);
            queue.add(root);
        }

        while (!queue.isEmpty()) {
            Long currentId = queue.poll();
            int currentGen = generationMap.get(currentId);

            // 传播到子女
            List<Long> children = parentToChildren.getOrDefault(currentId, List.of());
            for (Long childId : children) {
                if (!generationMap.containsKey(childId)) {
                    generationMap.put(childId, currentGen + 1);
                    queue.add(childId);
                }
            }

            // 同步配偶
            Set<Long> spouses = spouseMap.getOrDefault(currentId, Set.of());
            for (Long spouseId : spouses) {
                if (!generationMap.containsKey(spouseId)) {
                    generationMap.put(spouseId, currentGen);
                    queue.add(spouseId);
                }
            }
        }

        // 未连接的节点默认第 1 世
        for (FamilyNode node : allNodes) {
            if (!generationMap.containsKey(node.getId())) {
                generationMap.put(node.getId(), FamilyTreeConsts.DEFAULT_GENERATION);
            }
        }

        // 持久化世代
        List<FamilyNode> toUpdate = new ArrayList<>();
        for (FamilyNode node : allNodes) {
            int newGen = generationMap.getOrDefault(node.getId(), FamilyTreeConsts.DEFAULT_GENERATION);
            if (!Objects.equals(node.getGeneration(), newGen)) {
                node.setGeneration(newGen);
                node.setUpdateTime(LocalDateTime.now());
                toUpdate.add(node);
            }
        }

        for (FamilyNode node : toUpdate) {
            familyNodeRepository.update(node);
        }

        if (!toUpdate.isEmpty()) {
            logger.info("Updated generations for {} nodes in family={}", toUpdate.size(), familyId);
        }
    }

    /**
     * 转换 GEDCOM 性别标识为系统性别编码
     *
     * @param sex GEDCOM 性别（M/F/U）
     * @return 1=男, 2=女, 0=未知
     */
    private Integer convertGedcomSex(String sex) {
        if (sex == null) {
            return 0;
        }
        return switch (sex.toUpperCase()) {
            case "M" -> 1;
            case "F" -> 2;
            default -> 0;
        };
    }

    /**
     * 解析 GEDCOM 日期格式
     * <p>
     * 支持格式：
     * - "15 JAN 2000"（GEDCOM 标准）
     * - "JAN 2000"（仅月年）
     * - "2000"（仅年）
     * - "2000-01-15"（ISO 格式兼容）
     * </p>
     *
     * @param dateStr 日期字符串
     * @return LocalDate，解析失败返回 null
     */
    private LocalDate parseGedcomDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        dateStr = dateStr.trim();

        // 尝试 ISO 格式
        try {
            return LocalDate.parse(dateStr, ISO_DATE_FORMAT);
        } catch (Exception ignored) {
            // 继续尝试 GEDCOM 格式
        }

        // 尝试 GEDCOM 格式: "15 JAN 2000" / "JAN 2000" / "2000"
        String[] parts = dateStr.split("\\s+");
        try {
            if (parts.length == 3) {
                int day = Integer.parseInt(parts[0]);
                int month = parseGedcomMonth(parts[1]);
                int year = Integer.parseInt(parts[2]);
                return LocalDate.of(year, month, day);
            } else if (parts.length == 2) {
                int month = parseGedcomMonth(parts[0]);
                int year = Integer.parseInt(parts[1]);
                return LocalDate.of(year, month, 1);
            } else if (parts.length == 1) {
                int year = Integer.parseInt(parts[0]);
                return LocalDate.of(year, 1, 1);
            }
        } catch (Exception ignored) {
            // 解析失败返回 null
        }

        return null;
    }

    /**
     * 解析 GEDCOM 月份缩写
     */
    private int parseGedcomMonth(String monthStr) {
        Integer month = GEDCOM_MONTH_MAP.get(monthStr.toUpperCase());
        if (month != null) {
            return month;
        }
        throw new IllegalArgumentException("无效的 GEDCOM 月份: " + monthStr);
    }

    /**
     * 构建 GEDCOM 月份映射表
     */
    private static Map<String, Integer> buildMonthMap() {
        Map<String, Integer> map = new HashMap<>(12);
        map.put("JAN", 1);
        map.put("FEB", 2);
        map.put("MAR", 3);
        map.put("APR", 4);
        map.put("MAY", 5);
        map.put("JUN", 6);
        map.put("JUL", 7);
        map.put("AUG", 8);
        map.put("SEP", 9);
        map.put("OCT", 10);
        map.put("NOV", 11);
        map.put("DEC", 12);
        return Map.copyOf(map);
    }
}
