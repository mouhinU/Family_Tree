package com.mouhin.family.tree.application.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mouhin.family.tree.common.dto.TreeNodeVO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.service.FamilyTreeDomainService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 族谱树形结构应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyTreeApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyTreeApplicationService.class);

    /** 树缓存最大家族数：家庭内部系统，远超实际规模，仅作内存保护 */
    private static final int MAX_CACHED_FAMILIES = 200;

    /** 树缓存过期时间：除写操作主动失效外的兜底，防止遗漏写路径导致长期脏读 */
    private static final Duration TREE_CACHE_TTL = Duration.ofMinutes(10);

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyTreeDomainService familyTreeDomainService;
    private final Counter treeBuildCounter;

    /** 整棵族谱树缓存（key=familyId）。族谱为读多写少场景，命中时省去全表查询与建树。
     *  注意：缓存值为构建好的 VO 列表，读取方只可以序列化展示，不得修改其内容。 */
    private final Cache<Long, List<TreeNodeVO>> fullTreeCache = Caffeine.newBuilder()
            .maximumSize(MAX_CACHED_FAMILIES)
            .expireAfterWrite(TREE_CACHE_TTL)
            .recordStats()
            .build();

    public FamilyTreeApplicationService(FamilyNodeRepository familyNodeRepository,
                                        FamilyRelationRepository familyRelationRepository,
                                        FamilyTreeDomainService familyTreeDomainService,
                                        MeterRegistry meterRegistry) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyTreeDomainService = familyTreeDomainService;
        this.treeBuildCounter = Counter.builder("family.tree.build")
                .description("族谱树构建次数")
                .tag("type", "full")
                .register(meterRegistry);
    }

    /**
     * 获取完整族谱树
     *
     * @param familyId 家族ID
     * @return 树形结构列表（可能有多个根节点）
     */
    public List<TreeNodeVO> getFullTree(Long familyId) {
        return fullTreeCache.get(familyId, fid -> {
            treeBuildCounter.increment();
            List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(fid);
            List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(fid);
            return familyTreeDomainService.buildTree(allNodes, allRelations);
        });
    }

    /**
     * 获取子树
     *
     * @param familyId 家族ID
     * @param nodeId   子树根节点ID
     * @return 子树结构
     */
    public TreeNodeVO getSubTree(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);
        return familyTreeDomainService.buildSubTreeForNode(node, allNodes, allRelations);
    }

    /**
     * 失效族谱树缓存。
     * 写路径多在事务内：延迟到提交后再失效，避免并发读在提交前用旧数据回填缓存。
     *
     * @param familyId 家族ID
     */
    public void evictFamilyTree(Long familyId) {
        if (familyId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fullTreeCache.invalidate(familyId);
                }
            });
        } else {
            fullTreeCache.invalidate(familyId);
        }
    }
}
