package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.OfferingDTO;
import com.mouhin.family.tree.common.dto.OfferingStatVO;
import com.mouhin.family.tree.common.dto.OfferingUserVO;
import com.mouhin.family.tree.common.enums.OfferingTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.entity.FamilyOfferingDO;
import com.mouhin.family.tree.persistence.entity.SysUserDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.persistence.mapper.FamilyOfferingMapper;
import com.mouhin.family.tree.persistence.mapper.SysUserMapper;
import com.mouhin.family.tree.service.FamilyOfferingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 祭奠服务实现类（上香烛 / 烧纸）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
@Service
public class FamilyOfferingServiceImpl implements FamilyOfferingService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyOfferingServiceImpl.class);

    /** 最近祭奠时间展示格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 用户已注销或缺失时的昵称兜底 */
    private static final String DEFAULT_NICKNAME = "未知用户";

    private final FamilyOfferingMapper familyOfferingMapper;
    private final FamilyNodeMapper familyNodeMapper;
    private final SysUserMapper sysUserMapper;

    public FamilyOfferingServiceImpl(FamilyOfferingMapper familyOfferingMapper,
                                     FamilyNodeMapper familyNodeMapper,
                                     SysUserMapper sysUserMapper) {
        this.familyOfferingMapper = familyOfferingMapper;
        this.familyNodeMapper = familyNodeMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offer(Long userId, OfferingDTO dto) {
        OfferingTypeEnum type = OfferingTypeEnum.fromCode(dto.getOfferingType());
        FamilyNodeDO node = getOwnedNode(userId, dto.getNodeId());
        if (node.getDeathDate() == null) {
            throw new BusinessException("只能为已故长辈上香烛或烧纸");
        }

        FamilyOfferingDO record = new FamilyOfferingDO();
        record.setUserId(userId);
        record.setNodeId(dto.getNodeId());
        record.setOfferingType(type.getCode());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        familyOfferingMapper.insert(record);

        logger.info("Offering recorded id={} type={} user={} node={}",
                record.getId(), type.getDescription(), userId, dto.getNodeId());
    }

    @Override
    public List<OfferingStatVO> listStatsByNode(Long userId, Long nodeId) {
        getOwnedNode(userId, nodeId);

        LambdaQueryWrapper<FamilyOfferingDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyOfferingDO::getNodeId, nodeId);
        List<FamilyOfferingDO> records = familyOfferingMapper.selectList(query);

        Map<Long, String> nicknameMap = buildNicknameMap(records);

        List<OfferingStatVO> stats = new ArrayList<>(OfferingTypeEnum.values().length);
        stats.add(buildStat(OfferingTypeEnum.INCENSE, records, nicknameMap));
        stats.add(buildStat(OfferingTypeEnum.PAPER, records, nicknameMap));
        return stats;
    }

    /**
     * 校验节点存在且归当前用户所有。
     *
     * @param userId 当前用户ID
     * @param nodeId 节点ID
     * @return 节点数据对象
     */
    private FamilyNodeDO getOwnedNode(Long userId, Long nodeId) {
        FamilyNodeDO node = familyNodeMapper.selectById(nodeId);
        if (node == null || !Objects.equals(node.getUserId(), userId)) {
            throw new BusinessException("节点不存在或无权操作");
        }
        return node;
    }

    /**
     * 批量查询记录涉及用户的昵称，构建 userId → nickname 映射。
     *
     * @param records 祭奠记录
     * @return 昵称映射
     */
    private Map<Long, String> buildNicknameMap(List<FamilyOfferingDO> records) {
        Set<Long> userIds = records.stream()
                .map(FamilyOfferingDO::getUserId)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = new HashMap<>((int) (userIds.size() / 0.75) + 1);
        if (userIds.isEmpty()) {
            return nicknameMap;
        }
        List<SysUserDO> users = sysUserMapper.selectBatchIds(userIds);
        users.forEach(user -> nicknameMap.put(user.getId(), user.getNickname()));
        return nicknameMap;
    }

    /**
     * 聚合指定类型的祭奠统计：总次数 + 人员明细（按累计次数降序）。
     *
     * @param type        祭奠类型
     * @param records     该节点的全部祭奠记录
     * @param nicknameMap 昵称映射
     * @return 祭奠统计视图对象
     */
    private OfferingStatVO buildStat(OfferingTypeEnum type, List<FamilyOfferingDO> records,
                                     Map<Long, String> nicknameMap) {
        OfferingStatVO stat = new OfferingStatVO();
        stat.setOfferingType(type.getCode());
        stat.setTypeName(type.getDescription());

        Map<Long, OfferingUserVO> userAgg = new LinkedHashMap<>();
        Map<Long, LocalDateTime> lastTimeMap = new HashMap<>(16);
        long total = 0L;
        for (FamilyOfferingDO record : records) {
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
            if (record.getCreateTime() != null && (prev == null || record.getCreateTime().isAfter(prev))) {
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
