package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyGeneration;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 族谱辈分（世代名称）应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyGenerationApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyGenerationApplicationService.class);

    private final FamilyGenerationRepository familyGenerationRepository;

    public FamilyGenerationApplicationService(FamilyGenerationRepository familyGenerationRepository) {
        this.familyGenerationRepository = familyGenerationRepository;
    }

    /**
     * 列出家族所有辈分名称
     *
     * @param familyId 家族ID
     * @return 辈分列表
     */
    public List<FamilyGenerationDTO> listGenerations(Long familyId) {
        List<FamilyGeneration> generations = familyGenerationRepository.findByFamilyId(familyId);
        return generations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 批量保存辈分名称
     *
     * @param familyId 家族ID
     * @param dtos     辈分数据列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveGenerations(Long familyId, List<FamilyGenerationDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        for (FamilyGenerationDTO dto : dtos) {
            if (dto.getGeneration() == null) {
                throw new BusinessException("世代不能为空");
            }
            if (dto.getGeneration() < 1 || dto.getGeneration() > FamilyTreeConsts.MAX_GENERATION_DEPTH) {
                throw new BusinessException("世代范围为 1 ~ " + FamilyTreeConsts.MAX_GENERATION_DEPTH);
            }
            saveSingleGeneration(familyId, dto);
        }
        logger.info("Saved {} generation names for family={}", dtos.size(), familyId);
    }

    private void saveSingleGeneration(Long familyId, FamilyGenerationDTO dto) {
        FamilyGeneration existing = familyGenerationRepository.findByFamilyIdAndGeneration(
                familyId, dto.getGeneration());
        boolean blank = dto.getName() == null || dto.getName().isBlank();

        if (blank) {
            if (existing != null) {
                familyGenerationRepository.removeById(existing.getId());
            }
            return;
        }

        if (existing != null) {
            existing.setName(dto.getName().trim());
            existing.setUpdateTime(LocalDateTime.now());
            familyGenerationRepository.update(existing);
        } else {
            FamilyGeneration entity = new FamilyGeneration();
            entity.setFamilyId(familyId);
            entity.setGeneration(dto.getGeneration());
            entity.setName(dto.getName().trim());
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            familyGenerationRepository.save(entity);
        }
    }

    private FamilyGenerationDTO toDTO(FamilyGeneration entity) {
        FamilyGenerationDTO dto = new FamilyGenerationDTO();
        dto.setId(entity.getId());
        dto.setGeneration(entity.getGeneration());
        dto.setName(entity.getName());
        return dto;
    }
}
