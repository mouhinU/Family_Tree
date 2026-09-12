package com.mouhin.family.tree.application.service;

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
import org.springframework.data.redis.core.RedisTemplate;
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

    /**
     * Redis 缓存 Key 前缀
     */
    private static final String TREE_CACHE_KEY_PREFIX = "family:tree:";

    /**
     * 树缓存过期时间：除写操作主动失效外的兜底，防止遗漏写路径导致长期脏读
     */
    private static final Duration TREE_CACHE_TTL = Duration.ofMinutes(10);

    private final FamilyNodeRepository familyNodeRepository;
    private final FamilyRelationRepository familyRelationRepository;
    private final FamilyTreeDomainService familyTreeDomainService;
    private final Counter treeBuildCounter;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 整棵族谱树缓存（key=familyId）。族谱为读多写少场景，命中时省去全表查询与建树。
     * 注意：缓存值为构建好的 VO 列表，读取方只可以序列化展示，不得修改其内容。
     *
     * @param familyNodeRepository     节点仓储
     * @param familyRelationRepository 关系仓储
     * @param familyTreeDomainService  树构建领域服务
     * @param meterRegistry            指标注册表
     * @param redisTemplate            Redis 模板（JSON 序列化）
     */
    public FamilyTreeApplicationService(FamilyNodeRepository familyNodeRepository,
                                        FamilyRelationRepository familyRelationRepository,
                                        FamilyTreeDomainService familyTreeDomainService,
                                        MeterRegistry meterRegistry,
                                        RedisTemplate<String, Object> redisTemplate) {
        this.familyNodeRepository = familyNodeRepository;
        this.familyRelationRepository = familyRelationRepository;
        this.familyTreeDomainService = familyTreeDomainService;
        this.redisTemplate = redisTemplate;
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
    @SuppressWarnings("unchecked")
    public List<TreeNodeVO> getFullTree(Long familyId) {
        String cacheKey = TREE_CACHE_KEY_PREFIX + familyId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof List) {
            return (List<TreeNodeVO>) cached;
        }

        treeBuildCounter.increment();
        List<FamilyNode> allNodes = familyNodeRepository.findByFamilyId(familyId);
        List<FamilyRelation> allRelations = familyRelationRepository.findByFamilyId(familyId);
        List<TreeNodeVO> tree = familyTreeDomainService.buildTree(allNodes, allRelations);
        redisTemplate.opsForValue().set(cacheKey, tree, TREE_CACHE_TTL);
        return tree;
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
                    redisTemplate.delete(TREE_CACHE_KEY_PREFIX + familyId);
                }
            });
        } else {
            redisTemplate.delete(TREE_CACHE_KEY_PREFIX + familyId);
        }
    }
}
