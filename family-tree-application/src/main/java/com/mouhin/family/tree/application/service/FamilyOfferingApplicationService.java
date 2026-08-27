package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.OfferingDTO;
import com.mouhin.family.tree.common.dto.OfferingStatVO;
import com.mouhin.family.tree.common.dto.OfferingUserVO;
import com.mouhin.family.tree.common.enums.OfferingTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.entity.FamilyOffering;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyOfferingRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 祭奠应用服务（上香烛 / 烧纸 / 送鲜花）
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyOfferingApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyOfferingApplicationService.class);

    /**
     * 最近祭奠时间展示格式
     */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 用户已注销或缺失时的昵称兜底
     */
    private static final String DEFAULT_NICKNAME = "未知用户";

    private final FamilyOfferingRepository familyOfferingRepository;
    private final FamilyNodeRepository familyNodeRepository;
    private final UserRepository userRepository;

    public FamilyOfferingApplicationService(FamilyOfferingRepository familyOfferingRepository,
                                            FamilyNodeRepository familyNodeRepository,
                                            UserRepository userRepository) {
        this.familyOfferingRepository = familyOfferingRepository;
        this.familyNodeRepository = familyNodeRepository;
        this.userRepository = userRepository;
    }

    /**
     * 记录祭奠
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param dto      祭奠请求
     */
    @Transactional(rollbackFor = Exception.class)
    public void offer(Long familyId, Long userId, OfferingDTO dto) {
        OfferingTypeEnum type = OfferingTypeEnum.fromCode(dto.getOfferingType());

        FamilyNode node = familyNodeRepository.findById(dto.getNodeId());
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }
        if (node.getDeathDate() == null) {
            throw new BusinessException("只能为已故长辈敬献祭品");
        }

        // 后端校验：目标必须是登录人的长辈（世代数更小，更靠近始祖）
        User currentUser = userRepository.findById(userId);
        if (currentUser != null && currentUser.getNodeId() != null) {
            FamilyNode userNode = familyNodeRepository.findById(currentUser.getNodeId());
            if (userNode != null && node.getGeneration() != null
                    && userNode.getGeneration() != null
                    && node.getGeneration() >= userNode.getGeneration()) {
                throw new BusinessException("只能为已故长辈敬献祭品");
            }
        }

        FamilyOffering record = new FamilyOffering();
        record.setUserId(userId);
        record.setFamilyId(familyId);
        record.setNodeId(dto.getNodeId());
        record.setOfferingType(type.getCode());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        familyOfferingRepository.save(record);

        logger.info("Offering recorded id={} type={} user={} node={} family={}", record.getId(), type.getDescription(), userId, dto.getNodeId(), familyId);
    }

    /**
     * 列出节点祭奠统计
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 祭奠统计列表（按类型分组）
     */
    public List<OfferingStatVO> listStatsByNode(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("节点不存在或无权操作");
        }

        List<FamilyOffering> records = familyOfferingRepository.findByNodeId(familyId, nodeId);
        Map<Long, String> nicknameMap = buildNicknameMap(records);

        List<OfferingStatVO> stats = new ArrayList<>(OfferingTypeEnum.values().length);
        stats.add(buildStat(OfferingTypeEnum.FLOWER, records, nicknameMap));
        stats.add(buildStat(OfferingTypeEnum.WORSHIP, records, nicknameMap));
        return stats;
    }

    private Map<Long, String> buildNicknameMap(List<FamilyOffering> records) {
        Set<Long> userIds = records.stream().map(FamilyOffering::getUserId).collect(Collectors.toSet());
        Map<Long, String> nicknameMap = new HashMap<>((int) (userIds.size() / 0.75) + 1);
        if (userIds.isEmpty()) {
            return nicknameMap;
        }
        List<User> users = userRepository.findByIds(new ArrayList<>(userIds));
        users.forEach(user -> nicknameMap.put(user.getId(), user.getNickname()));
        return nicknameMap;
    }

    private OfferingStatVO buildStat(OfferingTypeEnum type, List<FamilyOffering> records,
                                     Map<Long, String> nicknameMap) {
        OfferingStatVO stat = new OfferingStatVO();
        stat.setOfferingType(type.getCode());
        stat.setTypeName(type.getDescription());

        Map<Long, OfferingUserVO> userAgg = new LinkedHashMap<>();
        Map<Long, LocalDateTime> lastTimeMap = new HashMap<>(16);
        long total = 0L;
        for (FamilyOffering record : records) {
            if (!Objects.equals(record.getOfferingType(), type.getCode())) {
                continue;
            }
            total++;
            Long uid = record.getUserId();
            OfferingUserVO userVO = userAgg.get(uid);
            if (userVO == null) {
                userVO = new OfferingUserVO();
                userVO.setUserId(uid);
                userVO.setNickname(nicknameMap.getOrDefault(uid, DEFAULT_NICKNAME));
                userVO.setCount(0L);
                userAgg.put(uid, userVO);
            }
            userVO.setCount(userVO.getCount() + 1);
            LocalDateTime prev = lastTimeMap.get(uid);
            if (record.getCreateTime() != null
                    && (prev == null || record.getCreateTime().isAfter(prev))) {
                lastTimeMap.put(uid, record.getCreateTime());
            }
        }

        List<OfferingUserVO> users = new ArrayList<>(userAgg.values());
        users.forEach(user -> {
            LocalDateTime lastTime = lastTimeMap.get(user.getUserId());
            user.setLastTime(lastTime != null ? lastTime.format(TIME_FORMATTER) : null);
        });
        users.sort(Comparator.comparing(OfferingUserVO::getCount, Comparator.reverseOrder()));

        stat.setTotalCount(total);
        stat.setUserCount(users.size());
        stat.setUsers(users);
        return stat;
    }
}
