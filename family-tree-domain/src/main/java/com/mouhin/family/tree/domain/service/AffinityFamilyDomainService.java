package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.AffinityTypeEnum;
import com.mouhin.family.tree.common.enums.GenderEnum;
import com.mouhin.family.tree.common.enums.NodeLinkTypeEnum;
import com.mouhin.family.tree.domain.entity.CrossFamilyLink;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.CrossFamilyLinkRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 亲族家族领域服务：给定族谱节点解析其父族/母族/妻族/夫族
 *
 * <p>解析口径（与 family_relation / family_node_link 正交定位一致）：</p>
 * <ul>
 *   <li>父族：节点自身的血缘同人链接指向的出身家族（过继/外嫁回指情形）优先，无链接回落本族；</li>
 *   <li>母族：族内亲子（含过继）边找到母亲节点，沿母亲的跨族链接找出身家族；</li>
 *   <li>妻族/夫族：族内夫妻边找到每个配偶节点，沿配偶的跨族链接找出身家族，多配偶则多条。</li>
 * </ul>
 *
 * <p>缺链时空着不猜测；仅按当前生效边解析，不做时间点回溯。本服务只读，
 * 解析结果供应用层组装 VO，绝不参与建树/世代修复等族内算法。</p>
 *
 * @author Family-Tree
 * @date 2026-11-20
 */
@Service
public class AffinityFamilyDomainService {

    private final CrossFamilyLinkRepository crossFamilyLinkRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyRepository familyRepository;

    public AffinityFamilyDomainService(CrossFamilyLinkRepository crossFamilyLinkRepository,
                                       FamilyNodeRepository familyNodeRepository,
                                       FamilyRelationRepository familyRelationRepository,
                                       FamilyRepository familyRepository) {
        this.crossFamilyLinkRepository = crossFamilyLinkRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyRepository = familyRepository;
    }

    /**
     * 解析节点的全部亲族家族
     *
     * @param node 本族族谱节点
     * @return 亲族家族视图列表（父族恒有一条：链接优先或回落本族），无可解析结果时仅含回落父族
     */
    public List<AffinityFamilyValueObject> resolveAffinityFamilies(FamilyNode node) {
        List<AffinityFamilyValueObject> result = new ArrayList<>();
        if (node == null || node.getId() == null || node.getFamilyId() == null) {
            return result;
        }
        Long familyId = node.getFamilyId();

        CrossFamilyLink bloodLink = findFirstBloodLink(node.getId());
        if (bloodLink != null) {
            collectViaLink(result, AffinityTypeEnum.FATHER, familyId, node, bloodLink);
        } else {
            result.add(new AffinityFamilyValueObject(AffinityTypeEnum.FATHER, familyId,
                    currentFamilyName(familyId), node.getId(), node.getName(), null, null, null));
        }

        List<FamilyRelation> relations = familyRelationRepository.findByNodeId(familyId, node.getId());
        Set<String> visited = new HashSet<>(relations.size() * 4 / 3 + 1);
        for (FamilyRelation relation : relations) {
            if (relation.isParentChild() || relation.isAdoption()) {
                appendMotherFamily(result, relation, node, visited);
            } else if (relation.isSpouse()) {
                appendSpouseFamily(result, relation, node, visited);
            }
        }
        return result;
    }

    /**
     * 母族解析：亲子关系中指向当前节点的母亲端沿链接找出身家族
     */
    private void appendMotherFamily(List<AffinityFamilyValueObject> result, FamilyRelation relation,
                                    FamilyNode node, Set<String> visited) {
        if (!Objects.equals(relation.getToNodeId(), node.getId())) {
            return;
        }
        FamilyNode mother = familyNodeRepository.findById(relation.getFromNodeId());
        if (mother == null || !Objects.equals(mother.getGender(), GenderEnum.FEMALE.getCode())) {
            return;
        }
        for (CrossFamilyLink link : crossFamilyLinkRepository.findByNodeId(mother.getId())) {
            collectViaLink(result, AffinityTypeEnum.MOTHER, node.getFamilyId(), mother, link, visited);
        }
    }

    /**
     * 妻族/夫族解析：夫妻边另一端配偶沿链接找出身家族，类型按配偶性别（性别缺失时按当前节点性别反推）
     */
    private void appendSpouseFamily(List<AffinityFamilyValueObject> result, FamilyRelation relation,
                                    FamilyNode node, Set<String> visited) {
        Long spouseId = relation.getSpouseId(node.getId());
        if (spouseId == null) {
            return;
        }
        FamilyNode spouse = familyNodeRepository.findById(spouseId);
        if (spouse == null) {
            return;
        }
        AffinityTypeEnum type = resolveSpouseAffinityType(spouse, node);
        if (type == null) {
            return;
        }
        for (CrossFamilyLink link : crossFamilyLinkRepository.findByNodeId(spouse.getId())) {
            collectViaLink(result, type, node.getFamilyId(), spouse, link, visited);
        }
    }

    /**
     * 沿链接解析另一端家族并追加视图；另一端家族即本族或缺失时跳过（缺链空着不猜测）
     */
    private void collectViaLink(List<AffinityFamilyValueObject> result, AffinityTypeEnum type,
                                Long currentFamilyId, FamilyNode relatedPerson, CrossFamilyLink link) {
        collectViaLink(result, type, currentFamilyId, relatedPerson, link, null);
    }

    /**
     * 沿链接解析另一端家族并追加视图，visited 提供 (类型,家族,对应人) 去重口径
     */
    private void collectViaLink(List<AffinityFamilyValueObject> result, AffinityTypeEnum type,
                                Long currentFamilyId, FamilyNode relatedPerson, CrossFamilyLink link,
                                Set<String> visited) {
        Long otherFamilyId = link.otherFamilyId(currentFamilyId);
        if (otherFamilyId == null || Objects.equals(otherFamilyId, currentFamilyId)) {
            return;
        }
        if (visited != null && !visited.add(type.name() + ":" + otherFamilyId + ":" + relatedPerson.getId())) {
            return;
        }
        Family otherFamily = familyRepository.findById(otherFamilyId);
        if (otherFamily == null) {
            return;
        }
        result.add(new AffinityFamilyValueObject(type, otherFamily.getId(), otherFamily.getName(),
                relatedPerson.getId(), relatedPerson.getName(),
                link.getId(), link.getSource(), link.getConfirmedBy()));
    }

    /**
     * 查询节点自身的血缘同人链接（父族回指只认 BLOOD 类型）
     */
    private CrossFamilyLink findFirstBloodLink(Long nodeId) {
        for (CrossFamilyLink link : crossFamilyLinkRepository.findByNodeId(nodeId)) {
            if (Objects.equals(link.getLinkType(), NodeLinkTypeEnum.BLOOD.getCode())) {
                return link;
            }
        }
        return null;
    }

    /**
     * 配偶亲族类型：配偶女→妻族、男→夫族；配偶性别缺失时按当前节点性别反推，均未知返回null
     */
    private AffinityTypeEnum resolveSpouseAffinityType(FamilyNode spouse, FamilyNode node) {
        if (Objects.equals(spouse.getGender(), GenderEnum.FEMALE.getCode())) {
            return AffinityTypeEnum.WIFE;
        }
        if (Objects.equals(spouse.getGender(), GenderEnum.MALE.getCode())) {
            return AffinityTypeEnum.HUSBAND;
        }
        if (Objects.equals(node.getGender(), GenderEnum.MALE.getCode())) {
            return AffinityTypeEnum.WIFE;
        }
        if (Objects.equals(node.getGender(), GenderEnum.FEMALE.getCode())) {
            return AffinityTypeEnum.HUSBAND;
        }
        return null;
    }

    /**
     * 回落父族的本族名称
     */
    private String currentFamilyName(Long familyId) {
        Family family = familyRepository.findById(familyId);
        return family != null ? family.getName() : null;
    }

    /**
     * 亲族家族解析结果值对象（不可变）
     *
     * @param type                亲族类型
     * @param familyId            亲族家族ID
     * @param familyName          亲族家族名称
     * @param relatedPersonNodeId 对应亲族成员节点ID（母族为母亲节点、妻/夫族为配偶节点、父族回指为其本人）
     * @param relatedPersonName   对应亲族成员姓名
     * @param linkId              依据的跨族链接ID，回落本族时为null
     * @param linkSource          成链来源编码（{@link com.mouhin.family.tree.common.enums.NodeLinkSourceEnum}），可为null
     * @param confirmedBy         链接确认人用户ID，账号自动来源为null
     * @author Family-Tree
     * @date 2026-11-20
     */
    public record AffinityFamilyValueObject(AffinityTypeEnum type,
                                            Long familyId,
                                            String familyName,
                                            Long relatedPersonNodeId,
                                            String relatedPersonName,
                                            Long linkId,
                                            Integer linkSource,
                                            Long confirmedBy) {
    }
}
