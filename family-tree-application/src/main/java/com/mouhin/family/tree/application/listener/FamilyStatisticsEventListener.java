package com.mouhin.family.tree.application.listener;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 家族统计事件监听器。
 * <p>
 * 使用 Redis 分布式缓存维护家族成员数和节点数的实时统计，
 * 并通过 Micrometer 计数器暴露 Prometheus 指标。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
@Component
public class FamilyStatisticsEventListener {

    private static final Logger logger = LoggerFactory.getLogger(FamilyStatisticsEventListener.class);

    /**
     * Redis Key 前缀
     */
    private static final String MEMBER_COUNT_KEY_PREFIX = "family:stats:member:";
    private static final String NODE_COUNT_KEY_PREFIX = "family:stats:node:";

    private static final Duration CACHE_EXPIRE_DURATION = Duration.ofMinutes(60);

    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final StringRedisTemplate stringRedisTemplate;

    private final Counter memberJoinCounter;
    private final Counter nodeCreateCounter;

    public FamilyStatisticsEventListener(FamilyMemberRepository familyMemberRepository,
                                         FamilyNodeRepository familyNodeRepository,
                                         MeterRegistry meterRegistry,
                                         StringRedisTemplate stringRedisTemplate) {
        this.familyMemberRepository = familyMemberRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.stringRedisTemplate = stringRedisTemplate;

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
            stringRedisTemplate.opsForValue().set(MEMBER_COUNT_KEY_PREFIX + event.familyId(),
                    String.valueOf(count), CACHE_EXPIRE_DURATION);
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
            stringRedisTemplate.opsForValue().set(NODE_COUNT_KEY_PREFIX + event.familyId(),
                    String.valueOf(count), CACHE_EXPIRE_DURATION);
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
        stringRedisTemplate.opsForValue().set(MEMBER_COUNT_KEY_PREFIX + event.familyId(),
                "1", CACHE_EXPIRE_DURATION);
        stringRedisTemplate.opsForValue().set(NODE_COUNT_KEY_PREFIX + event.familyId(),
                "0", CACHE_EXPIRE_DURATION);
        logger.debug("Initialized statistics cache for new family={}", event.familyId());
    }

    /**
     * 获取家族成员数量，优先从缓存读取，缓存未命中时从数据库加载。
     *
     * @param familyId 家族ID
     * @return 成员数量
     */
    public long getMemberCount(Long familyId) {
        String cached = stringRedisTemplate.opsForValue().get(MEMBER_COUNT_KEY_PREFIX + familyId);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        long count = familyMemberRepository.countByFamilyId(familyId);
        stringRedisTemplate.opsForValue().set(MEMBER_COUNT_KEY_PREFIX + familyId,
                String.valueOf(count), CACHE_EXPIRE_DURATION);
        return count;
    }

    /**
     * 获取家族节点数量，优先从缓存读取，缓存未命中时从数据库加载。
     *
     * @param familyId 家族ID
     * @return 节点数量
     */
    public long getNodeCount(Long familyId) {
        String cached = stringRedisTemplate.opsForValue().get(NODE_COUNT_KEY_PREFIX + familyId);
        if (cached != null) {
            return Long.parseLong(cached);
        }
        long count = familyNodeRepository.countByFamilyId(familyId);
        stringRedisTemplate.opsForValue().set(NODE_COUNT_KEY_PREFIX + familyId,
                String.valueOf(count), CACHE_EXPIRE_DURATION);
        return count;
    }
}
