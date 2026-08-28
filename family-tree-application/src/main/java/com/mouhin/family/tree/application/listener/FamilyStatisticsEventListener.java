package com.mouhin.family.tree.application.listener;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mouhin.family.tree.domain.event.FamilyCreatedEvent;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.event.NodeCreatedEvent;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 家族统计事件监听器。
 * <p>
 * 使用 Caffeine 本地缓存维护家族成员数和节点数的实时统计，
 * 并通过 Micrometer 计数器暴露 Prometheus 指标。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class FamilyStatisticsEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FamilyStatisticsEventListener.class);

    private static final int MAX_CACHE_SIZE = 10_000;
    private static final int CACHE_EXPIRE_MINUTES = 60;

    private final Cache<Long, Long> memberCountCache;
    private final Cache<Long, Long> nodeCountCache;

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyNodeRepository familyNodeRepository;

    private final Counter memberJoinCounter;
    private final Counter nodeCreateCounter;

    public FamilyStatisticsEventListener(FamilyMemberRepository familyMemberRepository,
                                         FamilyNodeRepository familyNodeRepository,
                                         MeterRegistry meterRegistry) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyNodeRepository = familyNodeRepository;

        this.memberCountCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .build();

        this.nodeCountCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES)
                .build();

        this.memberJoinCounter = Counter.builder("family.member.join")
                .description("Total number of member join events")
                .register(meterRegistry);

        this.nodeCreateCounter = Counter.builder("family.node.create")
                .description("Total number of node creation events")
                .register(meterRegistry);
    }

    /**
     * 成员加入时刷新成员计数缓存并递增指标。
     *
     * @param event 成员加入事件
     */
    @EventListener
    public void handleMemberJoined(MemberJoinedEvent event) {
        try {
            long count = familyMemberRepository.countByFamilyId(event.familyId());
            memberCountCache.put(event.familyId(), count);
            memberJoinCounter.increment();
            logger.debug("Refreshed member count for family={}, count={}", event.familyId(), count);
        } catch (Exception e) {
            logger.error("Failed to refresh member count for family={}", event.familyId(), e);
        }
    }

    /**
     * 节点创建时刷新节点计数缓存并递增指标。
     *
     * @param event 节点创建事件
     */
    @EventListener
    public void handleNodeCreated(NodeCreatedEvent event) {
        try {
            long count = familyNodeRepository.countByFamilyId(event.familyId());
            nodeCountCache.put(event.familyId(), count);
            nodeCreateCounter.increment();
            logger.debug("Refreshed node count for family={}, count={}", event.familyId(), count);
        } catch (Exception e) {
            logger.error("Failed to refresh node count for family={}", event.familyId(), e);
        }
    }

    /**
     * 家族创建时初始化缓存（1个成员，0个节点）。
     *
     * @param event 家族创建事件
     */
    @EventListener
    public void handleFamilyCreated(FamilyCreatedEvent event) {
        memberCountCache.put(event.familyId(), 1L);
        nodeCountCache.put(event.familyId(), 0L);
        logger.debug("Initialized statistics cache for new family={}", event.familyId());
    }

    /**
     * 获取家族成员数量，优先从缓存读取，缓存未命中时从数据库加载。
     *
     * @param familyId 家族ID
     * @return 成员数量
     */
    public long getMemberCount(Long familyId) {
        Long cached = memberCountCache.getIfPresent(familyId);
        if (cached != null) {
            return cached;
        }
        long count = familyMemberRepository.countByFamilyId(familyId);
        memberCountCache.put(familyId, count);
        return count;
    }

    /**
     * 获取家族节点数量，优先从缓存读取，缓存未命中时从数据库加载。
     *
     * @param familyId 家族ID
     * @return 节点数量
     */
    public long getNodeCount(Long familyId) {
        Long cached = nodeCountCache.getIfPresent(familyId);
        if (cached != null) {
            return cached;
        }
        long count = familyNodeRepository.countByFamilyId(familyId);
        nodeCountCache.put(familyId, count);
        return count;
    }
}
