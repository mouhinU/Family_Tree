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
     * 获取用户已设置的所有辈分名称
     *
     * @param userId 当前用户ID
     * @return 辈分列表（按世代升序）
     */
    List<FamilyGenerationDTO> listGenerations(Long userId);

    /**
     * 批量保存辈分名称（新增或更新，名称为空表示清除该世代）
     *
     * @param userId 当前用户ID
     * @param dtos   辈分列表
     */
    void saveGenerations(Long userId, List<FamilyGenerationDTO> dtos);
}
