package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关系校验领域服务。
 * <p>
 * 负责夫妻关系、亲子/收养关系创建前的合法性校验，
 * 包含自身校验、重复校验、直系血亲禁止、同胞禁止、重婚禁止等规则。
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class RelationValidationDomainService {

    /**
     * 校验夫妻关系合法性。
     * <p>
     * 校验规则：
     * 1. 不能与自身建立夫妻关系
     * 2. 不能重复建立（含反向）
     * 3. 直系血亲禁止结婚
     * 4. 同胞（共享父母）禁止结婚
     * 5. 双方均不得有未终止的婚姻（重婚禁止）
     *
     * @param familyId          家族ID
     * @param fromNodeId        起始节点ID
     * @param toNodeId          目标节点ID
     * @param existingRelations 涉及双方的现有关系列表
     * @param allRelations      家族所有关系列表
     */
    public void validateSpouseRelation(Long familyId, Long fromNodeId,
                                       Long toNodeId,
                                       List<FamilyRelation> existingRelations,
                                       List<FamilyRelation> allRelations) {
        // 1. 自身校验
        if (Objects.equals(fromNodeId, toNodeId)) {
            throw new BusinessException("不能与自身建立夫妻关系");
        }

        // 2. 重复校验（含反向）
        boolean duplicateExists = existingRelations.stream()
                .filter(r -> r.isSpouse())
                .anyMatch(r -> isBidirectionalMatch(r, fromNodeId, toNodeId));
        if (duplicateExists) {
            throw new BusinessException("该夫妻关系已存在");
        }

        // 3. 直系血亲禁止结婚
        boolean directBloodExists = existingRelations.stream()
                .filter(FamilyRelation::isParentChild)
                .anyMatch(r -> isBidirectionalMatch(r, fromNodeId, toNodeId));
        if (directBloodExists) {
            throw new BusinessException("直系血亲不能建立夫妻关系");
        }

        // 4. 同胞（共享父母）禁止结婚
        Set<Long> fromParents = collectParentIds(fromNodeId, allRelations);
        Set<Long> toParents = collectParentIds(toNodeId, allRelations);
        fromParents.retainAll(toParents);
        if (!fromParents.isEmpty()) {
            throw new BusinessException("同胞兄妹不能建立夫妻关系");
        }

        // 5. 重婚校验：双方均不得有未终止的婚姻
        validateNoActiveMarriage(fromNodeId, allRelations);
        validateNoActiveMarriage(toNodeId, allRelations);
    }

    /**
     * 校验新建关系的通用合法性
     *
     * @param familyId          家族ID
     * @param fromNodeId        起始节点ID
     * @param toNodeId          目标节点ID
     * @param relationType      关系类型
     * @param existingRelations 涉及双方的现有关系列表
     */
    public void validateRelationCreate(Long familyId, Long fromNodeId,
                                       Long toNodeId, Integer relationType,
                                       List<FamilyRelation> existingRelations) {
        // 自身校验
        if (Objects.equals(fromNodeId, toNodeId)) {
            throw new BusinessException("不能与自身建立关系");
        }

        // 重复校验（含反向）
        boolean duplicateExists = existingRelations.stream()
                .filter(r -> Objects.equals(r.getRelationType(), relationType))
                .anyMatch(r -> isBidirectionalMatch(r, fromNodeId, toNodeId));
        if (duplicateExists) {
            throw new BusinessException("该关系已存在");
        }

        // 夫妻关系额外校验
        if (Objects.equals(relationType, RelationTypeEnum.SPOUSE.getCode())) {
            // 此处仅做基本的重复和自身校验，完整的配偶合法性校验
            // 需要 allRelations，由 validateSpouseRelation 负责
        }
    }

    /**
     * 判断关系是否为双向匹配（from→to 或 to→from）
     */
    private boolean isBidirectionalMatch(FamilyRelation relation,
                                         Long nodeId1, Long nodeId2) {
        return (Objects.equals(relation.getFromNodeId(), nodeId1)
                && Objects.equals(relation.getToNodeId(), nodeId2))
                || (Objects.equals(relation.getFromNodeId(), nodeId2)
                && Objects.equals(relation.getToNodeId(), nodeId1));
    }

    /**
     * 收集指定节点的所有父母节点ID
     */
    private Set<Long> collectParentIds(Long nodeId,
                                       List<FamilyRelation> allRelations) {
        return allRelations.stream()
                .filter(FamilyRelation::isParentChild)
                .filter(r -> Objects.equals(r.getToNodeId(), nodeId))
                .map(FamilyRelation::getFromNodeId)
                .collect(Collectors.toSet());
    }

    /**
     * 校验指定节点不存在未终止的婚姻
     */
    private void validateNoActiveMarriage(Long nodeId,
                                          List<FamilyRelation> allRelations) {
        boolean hasActiveMarriage = allRelations.stream()
                .filter(FamilyRelation::isSpouse)
                .filter(r -> Objects.equals(r.getFromNodeId(), nodeId)
                        || Objects.equals(r.getToNodeId(), nodeId))
                .anyMatch(FamilyRelation::isActiveMarriage);
        if (hasActiveMarriage) {
            throw new BusinessException("该成员已有在婚配偶，不能重复建立夫妻关系");
        }
    }
}
