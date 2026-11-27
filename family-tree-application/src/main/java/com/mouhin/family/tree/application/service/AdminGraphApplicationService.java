package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AdminGraphDetailVO;
import com.mouhin.family.tree.common.dto.AdminGraphEdgeVO;
import com.mouhin.family.tree.common.dto.AdminGraphFamilyVO;
import com.mouhin.family.tree.common.dto.AdminGraphLinkVO;
import com.mouhin.family.tree.common.dto.AdminGraphNetworkVO;
import com.mouhin.family.tree.common.dto.AdminGraphNodeVO;
import com.mouhin.family.tree.common.dto.AdminGraphOverviewVO;
import com.mouhin.family.tree.common.dto.AdminGraphRelationVO;
import com.mouhin.family.tree.common.dto.CrossFamilyLinkIssueVO;
import com.mouhin.family.tree.common.enums.CrossFamilyLinkIssueEnum;
import com.mouhin.family.tree.common.enums.GenderEnum;
import com.mouhin.family.tree.common.enums.NodeLinkSourceEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.service.CrossFamilyLinkQualityDomainService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 平台跨族图谱应用服务：聚合跨族链接并输出治理视图。
 *
 * <p>本服务只读 family_node_link 及其端点引用，不修改 link，不参与族内建树、世代修复或亲属路径。</p>
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@Service
public class AdminGraphApplicationService {

    private static final int MAX_FAMILIES = 500;
    private static final int MAX_LINKS = 5000;
    private static final int NODE_BATCH_SIZE = 500;
    private static final String MISSING_FAMILY_NAME_PREFIX = "已删除家族 #";
    private static final String MISSING_NODE_NAME_PREFIX = "已删除节点 #";

    private final FamilyRepository familyRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final CrossFamilyLinkRepository crossFamilyLinkRepository;
    private final CrossFamilyLinkQualityDomainService qualityDomainService;

    public AdminGraphApplicationService(FamilyRepository familyRepository,
                                        FamilyNodeRepository familyNodeRepository,
                                        FamilyRelationRepository familyRelationRepository,
                                        CrossFamilyLinkRepository crossFamilyLinkRepository,
                                        CrossFamilyLinkQualityDomainService qualityDomainService) {
        this.familyRepository = familyRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.crossFamilyLinkRepository = crossFamilyLinkRepository;
        this.qualityDomainService = qualityDomainService;
    }

    /**
     * 获取家族级跨族图谱总览
     *
     * @return 家族节点、家族聚合边与跨族链接统计
     */
    public AdminGraphOverviewVO getOverview() {
        List<Family> families = familyRepository.listAll();
        List<CrossFamilyLink> allLinks = crossFamilyLinkRepository.listAll();
        boolean linksTruncated = allLinks.size() > MAX_LINKS;
        List<CrossFamilyLink> links = linksTruncated
                ? new ArrayList<>(allLinks.subList(0, MAX_LINKS)) : allLinks;

        Map<Long, Family> familyMap = toFamilyMap(families);
        Map<Long, FamilyNode> nodeMap = loadNodes(links);
        Map<Long, AdminGraphFamilyVO> familySummaries = new LinkedHashMap<>();
        for (Family family : families) {
            familySummaries.put(family.getId(), emptyFamilySummary(family));
        }

        Map<String, AdminGraphEdgeVO> edgeMap = new LinkedHashMap<>();
        long bloodLinkCount = 0L;
        long marriageLinkCount = 0L;
        long issueLinkCount = 0L;
        for (CrossFamilyLink link : links) {
            FamilyNode srcNode = nodeMap.get(link.getSrcNodeId());
            FamilyNode dstNode = nodeMap.get(link.getDstNodeId());
            List<CrossFamilyLinkIssueEnum> issues = qualityDomainService.inspect(link, srcNode, dstNode,
                    familyMap.containsKey(link.getSrcFamilyId()), familyMap.containsKey(link.getDstFamilyId()));
            AdminGraphFamilyVO srcFamily = ensureFamilySummary(familySummaries, familyMap, link.getSrcFamilyId());
            AdminGraphFamilyVO dstFamily = ensureFamilySummary(familySummaries, familyMap, link.getDstFamilyId());
            incrementFamily(srcFamily, link, !issues.isEmpty());
            if (!Objects.equals(srcFamily, dstFamily)) {
                incrementFamily(dstFamily, link, !issues.isEmpty());
            }
            if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.BLOOD.getCode())) {
                bloodLinkCount++;
            } else if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.MARRIAGE.getCode())) {
                marriageLinkCount++;
            }
            if (!issues.isEmpty()) {
                issueLinkCount++;
            }
            if (link.getSrcFamilyId() != null && link.getDstFamilyId() != null
                    && !Objects.equals(link.getSrcFamilyId(), link.getDstFamilyId())) {
                incrementEdge(edgeMap, link, !issues.isEmpty());
            }
        }

        List<AdminGraphFamilyVO> displayedFamilies = new ArrayList<>(familySummaries.values());
        displayedFamilies.sort(Comparator.comparing(AdminGraphFamilyVO::getLinkCount).reversed()
                .thenComparing(AdminGraphFamilyVO::getId));
        boolean familiesTruncated = displayedFamilies.size() > MAX_FAMILIES;
        if (familiesTruncated) {
            displayedFamilies = new ArrayList<>(displayedFamilies.subList(0, MAX_FAMILIES));
        }
        Set<Long> displayedFamilyIds = new LinkedHashSet<>();
        for (AdminGraphFamilyVO family : displayedFamilies) {
            displayedFamilyIds.add(family.getId());
        }
        List<AdminGraphEdgeVO> edges = new ArrayList<>(edgeMap.values());
        edges.removeIf(edge -> !displayedFamilyIds.contains(edge.getSrcFamilyId())
                || !displayedFamilyIds.contains(edge.getDstFamilyId()));
        edges.sort(Comparator.comparing(AdminGraphEdgeVO::getSrcFamilyId)
                .thenComparing(AdminGraphEdgeVO::getDstFamilyId));

        AdminGraphOverviewVO result = new AdminGraphOverviewVO();
        result.setGeneratedTime(LocalDateTime.now());
        result.setFamilyCount((long) familySummaries.size());
        result.setLinkedFamilyCount(familySummaries.values().stream()
                .filter(item -> item.getLinkCount() > 0L).count());
        result.setLinkCount((long) links.size());
        result.setBloodLinkCount(bloodLinkCount);
        result.setMarriageLinkCount(marriageLinkCount);
        result.setIssueLinkCount(issueLinkCount);
        result.setTruncated(linksTruncated || familiesTruncated);
        result.setFamilies(displayedFamilies);
        result.setEdges(edges);
        return result;
    }

    /**
     * 获取指定家族的跨族链接下钻明细
     *
     * @param familyId 家族ID
     * @return 家族摘要、端点节点与链接明细
     */
    public AdminGraphDetailVO getFamilyDetail(Long familyId) {
        if (familyId == null) {
            throw new BusinessException("缺少家族ID");
        }
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }
        List<CrossFamilyLink> allLinks = crossFamilyLinkRepository.findByFamilyId(familyId);
        boolean truncated = allLinks.size() > MAX_LINKS;
        List<CrossFamilyLink> links = truncated
                ? new ArrayList<>(allLinks.subList(0, MAX_LINKS)) : allLinks;
        Map<Long, FamilyNode> nodeMap = loadNodes(links);

        Set<Long> relatedFamilyIds = new LinkedHashSet<>();
        relatedFamilyIds.add(familyId);
        Set<Long> endpointNodeIds = new LinkedHashSet<>();
        for (CrossFamilyLink link : links) {
            if (link.getSrcFamilyId() != null) {
                relatedFamilyIds.add(link.getSrcFamilyId());
            }
            if (link.getDstFamilyId() != null) {
                relatedFamilyIds.add(link.getDstFamilyId());
            }
            if (link.getSrcNodeId() != null) {
                endpointNodeIds.add(link.getSrcNodeId());
            }
            if (link.getDstNodeId() != null) {
                endpointNodeIds.add(link.getDstNodeId());
            }
        }
        Map<Long, Family> familyMap = toFamilyMap(familyRepository.findByIds(new ArrayList<>(relatedFamilyIds)));
        familyMap.put(familyId, family);

        Map<Long, AdminGraphNodeVO> nodeViews = new LinkedHashMap<>();
        for (Long nodeId : endpointNodeIds) {
            FamilyNode node = nodeMap.get(nodeId);
            Long nodeFamilyId = node == null ? findRecordedFamilyId(links, nodeId) : node.getFamilyId();
            nodeViews.put(nodeId, toNodeView(nodeId, node, nodeFamilyId, familyMap));
        }
        List<AdminGraphNodeVO> nodes = new ArrayList<>(nodeViews.values());
        nodes.sort(Comparator.comparing(AdminGraphNodeVO::getFamilyId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(AdminGraphNodeVO::getId));

        List<AdminGraphLinkVO> linkViews = new ArrayList<>();
        AdminGraphFamilyVO familySummary = emptyFamilySummary(family);
        for (CrossFamilyLink link : links) {
            FamilyNode srcNode = nodeMap.get(link.getSrcNodeId());
            FamilyNode dstNode = nodeMap.get(link.getDstNodeId());
            List<CrossFamilyLinkIssueEnum> issues = qualityDomainService.inspect(link, srcNode, dstNode,
                    familyMap.containsKey(link.getSrcFamilyId()), familyMap.containsKey(link.getDstFamilyId()));
            linkViews.add(toLinkView(link, srcNode, dstNode, familyMap, issues));
            if (Objects.equals(link.getSrcFamilyId(), familyId) || Objects.equals(link.getDstFamilyId(), familyId)) {
                incrementFamily(familySummary, link, !issues.isEmpty());
            }
        }

        AdminGraphDetailVO result = new AdminGraphDetailVO();
        result.setFamily(familySummary);
        result.setNodes(nodes);
        result.setLinks(linkViews);
        result.setTruncated(truncated);
        return result;
    }

    /**
     * 获取指定家族的展示型图谱网络
     *
     * <p>包含族内节点与关系，以及跨族链接的外部端点；只读，不改变原有族谱树构建结果。</p>
     *
     * @param familyId 家族ID
     * @return 家族节点、族内关系与跨族链接
     */
    public AdminGraphNetworkVO getFamilyNetwork(Long familyId) {
        if (familyId == null) {
            throw new BusinessException("缺少家族ID");
        }
        Family family = familyRepository.findById(familyId);
        if (family == null) {
            throw new BusinessException("家族不存在");
        }

        List<FamilyNode> familyNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> familyRelations = familyRelationRepository.findByFamilyId(familyId);
        List<CrossFamilyLink> allLinks = crossFamilyLinkRepository.findByFamilyId(familyId);
        boolean truncated = allLinks.size() > MAX_LINKS;
        List<CrossFamilyLink> links = truncated
                ? new ArrayList<>(allLinks.subList(0, MAX_LINKS)) : allLinks;
        Map<Long, FamilyNode> endpointNodeMap = loadNodes(links);

        Set<Long> relatedFamilyIds = new LinkedHashSet<>();
        relatedFamilyIds.add(familyId);
        for (CrossFamilyLink link : links) {
            if (link.getSrcFamilyId() != null) {
                relatedFamilyIds.add(link.getSrcFamilyId());
            }
            if (link.getDstFamilyId() != null) {
                relatedFamilyIds.add(link.getDstFamilyId());
            }
        }
        Map<Long, Family> familyMap = toFamilyMap(familyRepository.findByIds(new ArrayList<>(relatedFamilyIds)));
        familyMap.put(familyId, family);

        Map<Long, AdminGraphNodeVO> nodeViews = new LinkedHashMap<>();
        for (FamilyNode node : familyNodes) {
            nodeViews.put(node.getId(), toNodeView(node.getId(), node, node.getFamilyId(), familyMap));
        }
        for (CrossFamilyLink link : links) {
            addNetworkEndpoint(nodeViews, endpointNodeMap, familyMap, link.getSrcNodeId(), link.getSrcFamilyId());
            addNetworkEndpoint(nodeViews, endpointNodeMap, familyMap, link.getDstNodeId(), link.getDstFamilyId());
        }
        List<AdminGraphNodeVO> nodes = new ArrayList<>(nodeViews.values());
        nodes.sort(Comparator.comparing((AdminGraphNodeVO node) -> !Objects.equals(node.getFamilyId(), familyId))
                .thenComparing(AdminGraphNodeVO::getFamilyId, Comparator.nullsLast(Long::compareTo))
                .thenComparing(AdminGraphNodeVO::getId));

        List<AdminGraphRelationVO> relationViews = new ArrayList<>();
        for (FamilyRelation relation : familyRelations) {
            if (!nodeViews.containsKey(relation.getFromNodeId()) || !nodeViews.containsKey(relation.getToNodeId())) {
                continue;
            }
            relationViews.add(toRelationView(relation));
        }
        relationViews.sort(Comparator.comparing(AdminGraphRelationVO::getId));

        List<AdminGraphLinkVO> linkViews = new ArrayList<>();
        AdminGraphFamilyVO familySummary = emptyFamilySummary(family);
        for (CrossFamilyLink link : links) {
            FamilyNode srcNode = endpointNodeMap.get(link.getSrcNodeId());
            FamilyNode dstNode = endpointNodeMap.get(link.getDstNodeId());
            List<CrossFamilyLinkIssueEnum> issues = qualityDomainService.inspect(link, srcNode, dstNode,
                    familyMap.containsKey(link.getSrcFamilyId()), familyMap.containsKey(link.getDstFamilyId()));
            linkViews.add(toLinkView(link, srcNode, dstNode, familyMap, issues));
            incrementFamily(familySummary, link, !issues.isEmpty());
        }

        AdminGraphNetworkVO result = new AdminGraphNetworkVO();
        result.setFamily(familySummary);
        result.setNodes(nodes);
        result.setRelations(relationViews);
        result.setCrossFamilyLinks(linkViews);
        result.setTruncated(truncated);
        return result;
    }

    private void addNetworkEndpoint(Map<Long, AdminGraphNodeVO> nodeViews,
                                    Map<Long, FamilyNode> nodeMap,
                                    Map<Long, Family> familyMap,
                                    Long nodeId,
                                    Long recordedFamilyId) {
        if (nodeId == null || nodeViews.containsKey(nodeId)) {
            return;
        }
        FamilyNode node = nodeMap.get(nodeId);
        Long familyId = node == null ? recordedFamilyId : node.getFamilyId();
        nodeViews.put(nodeId, toNodeView(nodeId, node, familyId, familyMap));
    }

    private AdminGraphRelationVO toRelationView(FamilyRelation relation) {
        AdminGraphRelationVO result = new AdminGraphRelationVO();
        result.setId(relation.getId());
        result.setFromNodeId(relation.getFromNodeId());
        result.setToNodeId(relation.getToNodeId());
        result.setRelationType(relation.getRelationType());
        result.setRelationTypeLabel(relationTypeLabel(relation.getRelationType()));
        result.setDivorced(relation.getDivorced());
        result.setWidowed(relation.getWidowed());
        return result;
    }

    private Map<Long, FamilyNode> loadNodes(List<CrossFamilyLink> links) {
        Set<Long> nodeIds = new LinkedHashSet<>();
        for (CrossFamilyLink link : links) {
            if (link.getSrcNodeId() != null) {
                nodeIds.add(link.getSrcNodeId());
            }
            if (link.getDstNodeId() != null) {
                nodeIds.add(link.getDstNodeId());
            }
        }
        if (nodeIds.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> idList = new ArrayList<>(nodeIds);
        Map<Long, FamilyNode> result = new HashMap<>();
        for (int start = 0; start < idList.size(); start += NODE_BATCH_SIZE) {
            int end = Math.min(start + NODE_BATCH_SIZE, idList.size());
            for (FamilyNode node : familyNodeRepository.findByIds(idList.subList(start, end))) {
                result.put(node.getId(), node);
            }
        }
        return result;
    }

    private Map<Long, Family> toFamilyMap(List<Family> families) {
        Map<Long, Family> result = new HashMap<>();
        for (Family family : families) {
            result.put(family.getId(), family);
        }
        return result;
    }

    private AdminGraphFamilyVO emptyFamilySummary(Family family) {
        AdminGraphFamilyVO result = new AdminGraphFamilyVO();
        result.setId(family.getId());
        result.setName(family.getName());
        result.setStatus(family.getStatus());
        result.setLinkCount(0L);
        result.setBloodLinkCount(0L);
        result.setMarriageLinkCount(0L);
        result.setIssueCount(0L);
        return result;
    }

    private AdminGraphFamilyVO ensureFamilySummary(Map<Long, AdminGraphFamilyVO> summaries,
                                                    Map<Long, Family> familyMap, Long familyId) {
        if (familyId == null) {
            return null;
        }
        return summaries.computeIfAbsent(familyId, key -> {
            Family family = familyMap.get(key);
            AdminGraphFamilyVO summary = new AdminGraphFamilyVO();
            summary.setId(key);
            summary.setName(family == null ? MISSING_FAMILY_NAME_PREFIX + key : family.getName());
            summary.setStatus(family == null ? null : family.getStatus());
            summary.setLinkCount(0L);
            summary.setBloodLinkCount(0L);
            summary.setMarriageLinkCount(0L);
            summary.setIssueCount(0L);
            return summary;
        });
    }

    private void incrementFamily(AdminGraphFamilyVO summary, CrossFamilyLink link, boolean hasIssue) {
        if (summary == null) {
            return;
        }
        summary.setLinkCount(summary.getLinkCount() + 1L);
        if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.BLOOD.getCode())) {
            summary.setBloodLinkCount(summary.getBloodLinkCount() + 1L);
        } else if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.MARRIAGE.getCode())) {
            summary.setMarriageLinkCount(summary.getMarriageLinkCount() + 1L);
        }
        if (hasIssue) {
            summary.setIssueCount(summary.getIssueCount() + 1L);
        }
    }

    private void incrementEdge(Map<String, AdminGraphEdgeVO> edgeMap, CrossFamilyLink link, boolean hasIssue) {
        long firstFamilyId = Math.min(link.getSrcFamilyId(), link.getDstFamilyId());
        long secondFamilyId = Math.max(link.getSrcFamilyId(), link.getDstFamilyId());
        String key = firstFamilyId + ":" + secondFamilyId;
        AdminGraphEdgeVO edge = edgeMap.computeIfAbsent(key, ignored -> {
            AdminGraphEdgeVO created = new AdminGraphEdgeVO();
            created.setSrcFamilyId(firstFamilyId);
            created.setDstFamilyId(secondFamilyId);
            created.setLinkCount(0L);
            created.setBloodLinkCount(0L);
            created.setMarriageLinkCount(0L);
            created.setIssueCount(0L);
            return created;
        });
        edge.setLinkCount(edge.getLinkCount() + 1L);
        if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.BLOOD.getCode())) {
            edge.setBloodLinkCount(edge.getBloodLinkCount() + 1L);
        } else if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.MARRIAGE.getCode())) {
            edge.setMarriageLinkCount(edge.getMarriageLinkCount() + 1L);
        }
        if (hasIssue) {
            edge.setIssueCount(edge.getIssueCount() + 1L);
        }
    }

    private AdminGraphNodeVO toNodeView(Long nodeId, FamilyNode node, Long familyId, Map<Long, Family> familyMap) {
        AdminGraphNodeVO result = new AdminGraphNodeVO();
        result.setId(nodeId);
        result.setFamilyId(familyId);
        Family family = familyId == null ? null : familyMap.get(familyId);
        result.setFamilyName(family == null ? MISSING_FAMILY_NAME_PREFIX + familyId : family.getName());
        result.setMissing(node == null);
        if (node != null) {
            result.setName(node.getName());
            result.setGender(node.getGender());
            result.setGenderLabel(node.getGender() == null ? null : GenderEnum.fromCode(node.getGender()).getDescription());
            result.setBirthYear(node.getBirthDate() == null ? null : node.getBirthDate().getYear());
            result.setDeathYear(node.getDeathDate() == null ? null : node.getDeathDate().getYear());
            result.setGeneration(node.getGeneration());
        } else {
            result.setName(MISSING_NODE_NAME_PREFIX + nodeId);
        }
        return result;
    }

    private AdminGraphLinkVO toLinkView(CrossFamilyLink link, FamilyNode srcNode, FamilyNode dstNode,
                                        Map<Long, Family> familyMap,
                                        List<CrossFamilyLinkIssueEnum> issues) {
        AdminGraphLinkVO result = new AdminGraphLinkVO();
        result.setId(link.getId());
        result.setSrcFamilyId(link.getSrcFamilyId());
        result.setSrcNodeId(link.getSrcNodeId());
        result.setSrcNodeName(srcNode == null ? MISSING_NODE_NAME_PREFIX + link.getSrcNodeId() : srcNode.getName());
        result.setDstFamilyId(link.getDstFamilyId());
        result.setDstNodeId(link.getDstNodeId());
        result.setDstNodeName(dstNode == null ? MISSING_NODE_NAME_PREFIX + link.getDstNodeId() : dstNode.getName());
        result.setLinkType(link.getLinkType());
        result.setLinkTypeLabel(linkTypeLabel(link.getLinkType()));
        result.setSource(link.getSource());
        result.setSourceLabel(linkSourceLabel(link.getSource()));
        result.setConfirmedBy(link.getConfirmedBy());
        result.setCreateTime(link.getCreateTime());
        List<CrossFamilyLinkIssueVO> issueViews = new ArrayList<>();
        for (CrossFamilyLinkIssueEnum issue : issues) {
            CrossFamilyLinkIssueVO issueView = new CrossFamilyLinkIssueVO();
            issueView.setCode(issue.getCode());
            issueView.setDescription(issue.getDescription());
            issueViews.add(issueView);
        }
        result.setIssues(issueViews);
        return result;
    }

    private Long findRecordedFamilyId(List<CrossFamilyLink> links, Long nodeId) {
        for (CrossFamilyLink link : links) {
            if (Objects.equals(link.getSrcNodeId(), nodeId)) {
                return link.getSrcFamilyId();
            }
            if (Objects.equals(link.getDstNodeId(), nodeId)) {
                return link.getDstFamilyId();
            }
        }
        return null;
    }

    private String relationTypeLabel(Integer code) {
        for (RelationTypeEnum value : RelationTypeEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return value.getDescription();
            }
        }
        return "未知(" + code + ")";
    }

    private String linkTypeLabel(Integer code) {
        for (NodeLinkTypeEnum value : NodeLinkTypeEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return value.getDescription();
            }
        }
        return "未知(" + code + ")";
    }

    private String linkSourceLabel(Integer code) {
        for (NodeLinkSourceEnum value : NodeLinkSourceEnum.values()) {
            if (Objects.equals(value.getCode(), code)) {
                return value.getDescription();
            }
        }
        return "未知(" + code + ")";
    }
}
