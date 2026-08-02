package com.mouhin.family.tree.service;

import com.mouhin.family.tree.common.dto.OfferingDTO;
import com.mouhin.family.tree.common.dto.OfferingStatVO;

import java.util.List;

/**
 * 祭奠服务接口（上香烛 / 烧纸）
 *
 * @author Family-Tree
 * @date 2026-08-01
 */
public interface FamilyOfferingService {

    /**
     * 记录一次祭奠操作（上香烛或烧纸），每次调用累计一次。
     *
     * @param userId 当前用户ID
     * @param dto    祭奠信息（节点ID + 类型）
     */
    void offer(Long userId, OfferingDTO dto);

    /**
     * 统计某已故节点的祭奠情况（香烛、烧纸各一项：总次数 + 人员明细）。
     *
     * @param userId 当前用户ID
     * @param nodeId 受祭节点ID
     * @return 祭奠统计列表（按类型固定顺序返回）
     */
    List<OfferingStatVO> listStatsByNode(Long userId, Long nodeId);
}
