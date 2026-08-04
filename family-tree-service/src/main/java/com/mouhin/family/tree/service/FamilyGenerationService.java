package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.FamilyGenerationDTO;

import java.util.List;

/**
 * 族谱辈分（世代名称）服务接口
 *
 * @author Family-Tree
 * @date 2026-07-30
 */
public interface FamilyGenerationService {

    /**
     * 获取家族已设置的所有辈分名称
     *
     * @param familyId 家族ID
     * @return 辈分列表（按世代升序）
     */
    List<FamilyGenerationDTO> listGenerations(Long familyId);

    /**
     * 批量保存辈分名称（新增或更新，名称为空表示清除该世代）
     *
     * @param familyId 家族ID
     * @param dtos     辈分列表
     */
    void saveGenerations(Long familyId, List<FamilyGenerationDTO> dtos);
}
