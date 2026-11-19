package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.event.FamilyTreeUpdatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.GenerationRepairDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 世代动态修复应用服务。
 * <p>
 * 结构写操作（新增/删除节点、创建/删除关系、导入重建全树）提交前调用修复内核，
 * 使全部节点的第几世与树深度保持一致。已维护辈分表的家族以辈分名为准，
 * 自动链路不覆盖；GEDCOM 导入整树重建沿用"结构即世代"口径无条件重算。
 *
 * @author Family-Tree
 * @date 2026-11-19
 */
@Service
public class GenerationRepairApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(GenerationRepairApplicationService.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyGenerationRepository familyGenerationRepository;
    private final GenerationRepairDomainService generationRepairDomainService;
    private final ApplicationEventPublisher eventPublisher;

    public GenerationRepairApplicationService(FamilyNodeRepository familyNodeRepository,
                                              FamilyRelationRepository familyRelationRepository,
                                              FamilyGenerationRepository familyGenerationRepository,
                                              GenerationRepairDomainService generationRepairDomainService,
                                              ApplicationEventPublisher eventPublisher) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyGenerationRepository = familyGenerationRepository;
        this.generationRepairDomainService = generationRepairDomainService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 未维护辈分表的家族才做结构修复；辈分名已登记的家族以辈分表为准，跳过
     *
     * @param familyId 家族ID
     * @return 修复结果（是否被辈分表拦截、更新节点数）
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResult repairIfUnmanaged(Long familyId) {
        if (!familyGenerationRepository.findByFamilyId(familyId).isEmpty()) {
            return new RepairResult(true, 0);
        }
        return new RepairResult(false, repairFamily(familyId));
    }

    /**
     * 按树结构重算并落库该家族全部节点的世代（不受辈分表拦截，供导入与平台修复复用）
     *
     * @param familyId 家族ID
     * @return 世代发生变化的节点数
     */
    @Transactional(rollbackFor = Exception.class)
    public int repairFamily(Long familyId) {
        List<FamilyNode> nodes = familyNodeRepository.findByFamilyId(familyId);
        if (nodes.isEmpty()) {
            return 0;
        }
        List<FamilyRelation> relations = familyRelationRepository.findByFamilyId(familyId);
        List<FamilyNode> changed = generationRepairDomainService.repair(nodes, relations);
        if (changed.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (FamilyNode node : changed) {
            node.setUpdateTime(now);
        }
        familyNodeRepository.batchUpdateGeneration(changed);
        logger.info("Repaired generations for {} node(s) in family={}", changed.size(), familyId);
        eventPublisher.publishEvent(FamilyTreeUpdatedEvent.of(familyId));
        return changed.size();
    }

    /**
     * 结构修复单次调用的结果
     *
     * @param generationManaged 家族是否已维护辈分表（true 时跳过修复）
     * @param updatedNodes      本次更新的节点数
     */
    public record RepairResult(boolean generationManaged, int updatedNodes) {
    }
}
