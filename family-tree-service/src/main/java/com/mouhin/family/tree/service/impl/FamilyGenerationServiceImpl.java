package com.mouhin.family.tree.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.persistence.entity.FamilyGenerationDO;
import com.mouhin.family.tree.persistence.mapper.FamilyGenerationMapper;
import com.mouhin.family.tree.service.FamilyGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 族谱辈分（世代名称）服务实现类
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
@Service
public class FamilyGenerationServiceImpl implements FamilyGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyGenerationServiceImpl.class);

    private final FamilyGenerationMapper familyGenerationMapper;

    public FamilyGenerationServiceImpl(FamilyGenerationMapper familyGenerationMapper) {
        this.familyGenerationMapper = familyGenerationMapper;
    }

    @Override
    public List<FamilyGenerationDTO> listGenerations(Long userId) {
        LambdaQueryWrapper<FamilyGenerationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyGenerationDO::getUserId, userId)
                .orderByAsc(FamilyGenerationDO::getGeneration);
        return familyGenerationMapper.selectList(query).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveGenerations(Long userId, List<FamilyGenerationDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (FamilyGenerationDTO dto : dtos) {
            if (dto.getGeneration() == null) {
                throw new BusinessException("世代不能为空");
            }
            saveSingleGeneration(userId, dto);
        }
        logger.info("Saved {} generation names for user={}", dtos.size(), userId);
    }

    private void saveSingleGeneration(Long userId, FamilyGenerationDTO dto) {
        FamilyGenerationDO existing = getByUserAndGeneration(userId, dto.getGeneration());
        boolean blank = dto.getName() == null || dto.getName().isBlank();

        if (blank) {
            // 名称为空表示清除该世代的辈分名
            if (existing != null) {
                familyGenerationMapper.deleteById(existing.getId());
            }
            return;
        }

        if (existing != null) {
            existing.setName(dto.getName().trim());
            existing.setUpdateTime(LocalDateTime.now());
            familyGenerationMapper.updateById(existing);
        } else {
            FamilyGenerationDO entity = new FamilyGenerationDO();
            entity.setUserId(userId);
            entity.setGeneration(dto.getGeneration());
            entity.setName(dto.getName().trim());
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            familyGenerationMapper.insert(entity);
        }
    }

    private FamilyGenerationDO getByUserAndGeneration(Long userId, Integer generation) {
        LambdaQueryWrapper<FamilyGenerationDO> query = new LambdaQueryWrapper<>();
        query.eq(FamilyGenerationDO::getUserId, userId)
                .eq(FamilyGenerationDO::getGeneration, generation)
                .last("LIMIT 1");
        return familyGenerationMapper.selectOne(query);
    }

    private FamilyGenerationDTO toDTO(FamilyGenerationDO entity) {
        FamilyGenerationDTO dto = new FamilyGenerationDTO();
        dto.setId(entity.getId());
        dto.setGeneration(entity.getGeneration());
        dto.setName(entity.getName());
        return dto;
    }
}
