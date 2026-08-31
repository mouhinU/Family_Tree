package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.BiographyUpdateDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.HtmlSanitizeUtils;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 人物传记应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class BiographyApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(BiographyApplicationService.class);

    private final FamilyNodeRepository familyNodeRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BiographyApplicationService(FamilyNodeRepository familyNodeRepository,
                                       ApplicationEventPublisher eventPublisher) {
        this.familyNodeRepository = familyNodeRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询人物传记（富文本）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @return 传记富文本，未撰写时返回空字符串
     */
    public String getBiography(Long familyId, Long nodeId) {
        FamilyNode node = getNodeChecked(familyId, nodeId);
        return node.getBiography() != null ? node.getBiography() : "";
    }

    /**
     * 更新人物传记（富文本入库前清洗）
     *
     * @param familyId 家族ID
     * @param nodeId   节点ID
     * @param userId   操作用户ID
     * @param username 操作用户名
     * @param dto      传记内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateBiography(Long familyId, Long nodeId, Long userId, String username,
                                BiographyUpdateDTO dto) {
        FamilyNode node = getNodeChecked(familyId, nodeId);
        String biography = dto.getBiography();
        if (biography != null && biography.length() > HtmlSanitizeUtils.MAX_RICH_TEXT_LENGTH) {
            throw new BusinessException("传记内容过长，请精简后保存");
        }
        String sanitized = HtmlSanitizeUtils.sanitize(biography);
        // 清洗后无实质内容时视为清空传记
        node.setBiography(sanitized == null || sanitized.isBlank() ? null : sanitized);
        node.setUpdateTime(LocalDateTime.now());
        familyNodeRepository.update(node);
        logger.info("用户 {} 更新节点 {} 的人物传记", userId, nodeId);
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "BIOGRAPHY_UPDATE",
                "更新人物传记: " + node.getName(), "node", nodeId, familyId, null));
    }

    private FamilyNode getNodeChecked(Long familyId, Long nodeId) {
        FamilyNode node = familyNodeRepository.findById(nodeId);
        if (node == null || !Objects.equals(node.getFamilyId(), familyId)) {
            throw new BusinessException("家族成员不存在");
        }
        return node;
    }
}
