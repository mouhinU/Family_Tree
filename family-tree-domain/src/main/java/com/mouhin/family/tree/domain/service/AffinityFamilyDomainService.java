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
 *   <li>出身家族统一规则：亲属在本族有血缘父边（亲子/收养且父端为男性）则出身即本族；
 *       无父边时沿其血缘同人链接找出身家族，多链接优先取"链接另一端在本族有父边"的一条
 *       （该端记载更可信）；均无则回落在本族本人；</li>
 *   <li>父族：按本人出身家族解析——有族内父边时对应人为父亲（父族=本族），
 *       无父边有链接时为过继/外嫁回指（对应人为本人）；两者皆无则出身不可考，
 *       不显示父族行（2026-11-22 修订：族内入赘/外嫁节点不再回落误显本族）；</li>
 *   <li>母族：族内亲子（含过继）边找到母亲节点，按母亲的出身家族解析；
 *       例外：母亲自身为本族亲生女（其在本族有父边）时，本族以其子女之母族显示
 *       （2026-11-22 修订：姚能刚/黄**类本族亲生女（蒙昌绪/蒙娥有族内父边）的异姓子显示母族=本族；
 *       林世强之母林*为入嫁方、出身无证据，仍不显示、不猜测）；</li>
 *   <li>妻族/夫族：族内夫妻边找到每个配偶节点，按配偶的出身家族解析，多配偶则多条，
 *       出身即本族时不显示；例外：当前节点自身非本族出身（无父边无回指，即入嫁方）而配偶在本族有父边时，
 *       本族即以妻族/夫族行显示给入嫁方（2026-11-22 修订：冉从桃/谭丽萍类节点不再误显父族，改显夫族）。</li>
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
     * @return 亲族家族视图列表（父族行仅在有族内父边或链接回指证据时出现），全部无证据时返回空列表
     */
    public List<AffinityFamilyValueObject> resolveAffinityFamilies(FamilyNode node) {
        List<AffinityFamilyValueObject> result = new ArrayList<>();
        if (node == null || node.getId() == null || node.getFamilyId() == null) {
            return result;
        }
        Long familyId = node.getFamilyId();
        List<FamilyRelation> relations = familyRelationRepository.findByNodeId(familyId, node.getId());

        OriginFamilyValueObject origin = resolveOriginFamily(node, relations);
        boolean hasFatherEvidence = hasOriginEvidence(node, origin);
        boolean nativeToCurrentFamily = Objects.equals(origin.familyId(), familyId) && hasFatherEvidence;
        if (hasFatherEvidence) {
            result.add(new AffinityFamilyValueObject(AffinityTypeEnum.FATHER, origin.familyId(), origin.familyName(),
                    origin.relatedPerson().getId(), origin.relatedPerson().getName(),
                    origin.linkId(), origin.linkSource(), origin.confirmedBy()));
        }

        Set<String> visited = new HashSet<>(16);
        for (FamilyRelation relation : relations) {
            if (relation.isParentChild() || relation.isAdoption()) {
                appendMotherFamily(result, relation, node, visited, nativeToCurrentFamily);
            } else if (relation.isSpouse()) {
                appendSpouseFamily(result, relation, node, visited, nativeToCurrentFamily);
            }
        }
        return result;
    }

    /**
     * 父族行是否可显示：族内父边（对应人为父亲）或血缘同人链接回指时才有证据；
     * 「本族本人」回落说明出身不可考，不显示——族内入赘/外嫁节点不得误显本族为父族
     *
     * @param node   当前解析节点
     * @param origin 出身家族解析结果
     * @return 有族内父边或链接回指证据时为true
     */
    private boolean hasOriginEvidence(FamilyNode node, OriginFamilyValueObject origin) {
        return origin.linkId() != null
                || !Objects.equals(origin.relatedPerson().getId(), node.getId());
    }

    /**
     * 母族解析：亲子关系中指向当前节点的母亲端按出身家族规则解析；
     * 母亲出身他族时显示他族母族；母亲为本族亲生女（本族内有父边证据）且当前节点非本族父系出身时，
     * 本族以其子女的母族显示（姚能刚/黄**类，2026-11-22 修订）；
     * 母亲入嫁本族且出身无证据时不显示（林世强类，不猜测）
     */
    private void appendMotherFamily(List<AffinityFamilyValueObject> result, FamilyRelation relation,
                                    FamilyNode node, Set<String> visited, boolean nativeToCurrentFamily) {
        if (!Objects.equals(relation.getToNodeId(), node.getId())) {
            return;
        }
        FamilyNode mother = familyNodeRepository.findById(relation.getFromNodeId());
        if (mother == null || !Objects.equals(mother.getGender(), GenderEnum.FEMALE.getCode())) {
            return;
        }
        OriginFamilyValueObject origin = resolveOriginFamily(mother, null);
        if (origin.familyId() == null) {
            return;
        }
        if (!Objects.equals(origin.familyId(), node.getFamilyId())) {
            addAffinityRow(result, AffinityTypeEnum.MOTHER, origin.familyId(), origin.familyName(),
                    mother, origin, visited);
        } else if (!nativeToCurrentFamily && hasOriginEvidence(mother, origin)) {
            addAffinityRow(result, AffinityTypeEnum.MOTHER, node.getFamilyId(),
                    currentFamilyName(node.getFamilyId()), mother, origin, visited);
        }
    }

    /**
     * 妻族/夫族解析：夫妻边另一端配偶按出身家族规则解析，类型按配偶性别（性别缺失时按当前节点性别反推），
     * 出身即本族时不显示；但当前节点为入嫁本族方（非本族出身）且配偶为本族出身（有族内父边）时，
     * 本族应以妻族/夫族身份显示给入嫁方（冉从桃/谭丽萍类节点，2026-11-22 修订）
     */
    private void appendSpouseFamily(List<AffinityFamilyValueObject> result, FamilyRelation relation,
                                    FamilyNode node, Set<String> visited, boolean nativeToCurrentFamily) {
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
        OriginFamilyValueObject origin = resolveOriginFamily(spouse, null);
        if (origin.familyId() != null && !Objects.equals(origin.familyId(), node.getFamilyId())) {
            addAffinityRow(result, type, origin.familyId(), origin.familyName(), spouse, origin, visited);
        } else if (!nativeToCurrentFamily && hasOriginEvidence(spouse, origin)) {
            Family currentFamily = familyRepository.findById(node.getFamilyId());
            if (currentFamily != null) {
                addAffinityRow(result, type, currentFamily.getId(), currentFamily.getName(), spouse, origin, visited);
            }
        }
    }

    /**
     * 去重后追加亲族视图行（键为 类型:家族:对应人）
     */
    private void addAffinityRow(List<AffinityFamilyValueObject> result, AffinityTypeEnum type,
                                Long familyId, String familyName, FamilyNode relative,
                                OriginFamilyValueObject origin, Set<String> visited) {
        if (!visited.add(type.name() + ":" + familyId + ":" + relative.getId())) {
            return;
        }
        result.add(new AffinityFamilyValueObject(type, familyId, familyName,
                relative.getId(), relative.getName(),
                origin.linkId(), origin.linkSource(), origin.confirmedBy()));
    }

    /**
     * 解析节点在本族视角下的出身家族：族内父边优先，其次血缘同人链接（优先链接另一端在本族有父边的一条），
     * 均无则回落本族本人
     *
     * @param node      待解析节点
     * @param relations 已取到的该节点双边关系边，null 时按节点所在家族现查
     * @return 出身家族视图
     */
    private OriginFamilyValueObject resolveOriginFamily(FamilyNode node, List<FamilyRelation> relations) {
        List<FamilyRelation> edges = relations != null
                ? relations
                : familyRelationRepository.findByNodeId(node.getFamilyId(), node.getId());
        FamilyNode father = findInFamilyFather(node, edges);
        if (father != null) {
            return new OriginFamilyValueObject(node.getFamilyId(), currentFamilyName(node.getFamilyId()),
                    father, null, null, null);
        }
        CrossFamilyLink bloodLink = findPreferredBloodLink(node);
        if (bloodLink != null) {
            Long otherFamilyId = bloodLink.otherFamilyId(node.getFamilyId());
            Family otherFamily = otherFamilyId != null ? familyRepository.findById(otherFamilyId) : null;
            if (otherFamily != null) {
                return new OriginFamilyValueObject(otherFamily.getId(), otherFamily.getName(), node,
                        bloodLink.getId(), bloodLink.getSource(), bloodLink.getConfirmedBy());
            }
        }
        return new OriginFamilyValueObject(node.getFamilyId(), currentFamilyName(node.getFamilyId()),
                node, null, null, null);
    }

    /**
     * 查找节点在本族的男性父端（亲子/收养边），存在即说明本族是其血脉延续之所
     */
    private FamilyNode findInFamilyFather(FamilyNode node, List<FamilyRelation> relations) {
        for (FamilyRelation relation : relations) {
            if (!(relation.isParentChild() || relation.isAdoption())
                    || !Objects.equals(relation.getToNodeId(), node.getId())) {
                continue;
            }
            FamilyNode parent = familyNodeRepository.findById(relation.getFromNodeId());
            if (parent != null && Objects.equals(parent.getGender(), GenderEnum.MALE.getCode())) {
                return parent;
            }
        }
        return null;
    }

    /**
     * 选取节点的血缘同人链接：多条时优先"另一端在其家族内有父边"的一条（同人记载源出身更可信），
     * 均不满足取首条；父族回指只认 BLOOD 类型
     */
    private CrossFamilyLink findPreferredBloodLink(FamilyNode node) {
        CrossFamilyLink first = null;
        for (CrossFamilyLink link : crossFamilyLinkRepository.findByNodeId(node.getId())) {
            if (!Objects.equals(link.getLinkType(), NodeLinkTypeEnum.BLOOD.getCode())) {
                continue;
            }
            if (first == null) {
                first = link;
            }
            FamilyNode other = familyNodeRepository.findById(link.otherNodeId(node.getId()));
            if (other == null || other.getFamilyId() == null) {
                continue;
            }
            List<FamilyRelation> otherRelations =
                    familyRelationRepository.findByNodeId(other.getFamilyId(), other.getId());
            if (findInFamilyFather(other, otherRelations) != null) {
                return link;
            }
        }
        return first;
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
     * 出身家族解析的内部视图
     *
     * @param familyId      出身家族ID
     * @param familyName    出身家族名称
     * @param relatedPerson 对应人：族内父边情形为父亲节点，回指/回落情形为本人
     * @param linkId        依据的跨族链接ID，无链接时为null
     * @param linkSource    成链来源编码，可为null
     * @param confirmedBy   链接确认人用户ID，可为null
     * @author Family-Tree
     * @date 2026-11-22
     */
    private record OriginFamilyValueObject(Long familyId, String familyName, FamilyNode relatedPerson,
                                           Long linkId, Integer linkSource, Long confirmedBy) {
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
