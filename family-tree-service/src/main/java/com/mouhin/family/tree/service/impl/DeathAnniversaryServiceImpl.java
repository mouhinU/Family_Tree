package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.persistence.entity.FamilyNodeDO;
import com.mouhin.family.tree.persistence.mapper.FamilyNodeMapper;
import com.mouhin.family.tree.service.DeathAnniversaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 忌日提醒服务实现类
 *
 * @author Family-Tree
 * @date 2026-08-04
 */
@Service
public class DeathAnniversaryServiceImpl implements DeathAnniversaryService {

    private static final Logger logger = LoggerFactory.getLogger(DeathAnniversaryServiceImpl.class);

    private final FamilyNodeMapper familyNodeMapper;

    public DeathAnniversaryServiceImpl(FamilyNodeMapper familyNodeMapper) {
        this.familyNodeMapper = familyNodeMapper;
    }

    @Override
    public List<DeathAnniversaryDTO> getUpcoming(Long familyId, int days) {
        LambdaQueryWrapper<FamilyNodeDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyNodeDO::getFamilyId, familyId)
                .isNotNull(FamilyNodeDO::getDeathDate);
        List<FamilyNodeDO> deceasedNodes = familyNodeMapper.selectList(query);

        LocalDate today = LocalDate.now();
        List<DeathAnniversaryDTO> result = new ArrayList<>();

        for (FamilyNodeDO node : deceasedNodes) {
            try {
                LocalDate deathDate = node.getDeathDate();
                // 计算今年忌日
                LocalDate thisYearDeath = deathDate.withYear(today.getYear());
                if (thisYearDeath.isBefore(today)) {
                    // 今年的忌日已过，计算明年的
                    thisYearDeath = deathDate.withYear(today.getYear() + 1);
                }
                long daysUntil = java.time.temporal.ChronoUnit.DAYS.between(today, thisYearDeath);
                if (daysUntil >= 0 && daysUntil <= days) {
                    DeathAnniversaryDTO dto = new DeathAnniversaryDTO();
                    dto.setNodeId(node.getId());
                    dto.setName(node.getName());
                    dto.setDeathDate(deathDate.toString());
                    dto.setDaysUntil((int) daysUntil);
                    result.add(dto);
                }
            } catch (Exception e) {
                logger.warn("Failed to calculate death anniversary for node {}: {}",
                        node.getId(), e.getMessage());
            }
        }
        result.sort((a, b) -> Integer.compare(a.getDaysUntil(), b.getDaysUntil()));
        return result;
    }
}
