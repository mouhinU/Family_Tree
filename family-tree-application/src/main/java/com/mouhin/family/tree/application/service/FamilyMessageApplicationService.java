package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.enums.MessageCategoryEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.domain.repository.FamilyMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 家族留言应用服务
 *
 * @author Family-Tree
 * @date 2026-08-25
 */
@Service
public class FamilyMessageApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyMessageApplicationService.class);

    /** 留言内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final FamilyMessageRepository familyMessageRepository;

    public FamilyMessageApplicationService(FamilyMessageRepository familyMessageRepository) {
        this.familyMessageRepository = familyMessageRepository;
    }

    /**
     * 发布留言
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @param username 用户名
     * @param dto      留言内容
     */
    @Transactional(rollbackFor = Exception.class)
    public void postMessage(Long familyId, Long userId, String username, MessageCreateDTO dto) {
        String content = dto.getContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException("留言内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("留言内容不能超过" + MAX_CONTENT_LENGTH + "字");
        }

        // 校验分类，默认普通留言
        String category = dto.getCategory();
        if (category != null && !category.isBlank()) {
            MessageCategoryEnum.fromCode(category);
        } else {
            category = MessageCategoryEnum.GENERAL.getCode();
        }

        FamilyMessage message = new FamilyMessage();
        message.setFamilyId(familyId);
        message.setUserId(userId);
        message.setUsername(username != null ? username : "匿名");
        message.setContent(content.trim());
        message.setLikeCount(0L);
        message.setCategory(category);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        familyMessageRepository.save(message);
        logger.info("用户 {} 在家族 {} 发布留言: id={}, category={}", userId, familyId, message.getId(), category);
    }

    /**
     * 分页查询留言列表
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID
     * @param category      留言分类（null 表示全部分类）
     * @param page          页码（从1开始）
     * @param size          每页大小
     * @return 分页结果
     */
    public PageResult<MessageVO> listMessages(Long familyId, Long currentUserId, String category, int page, int size) {
        long total = familyMessageRepository.countByFamilyId(familyId, category);

        if (total == 0) {
            return new PageResult<>(List.of(), 0L, page, size);
        }

        int offset = (page - 1) * size;
        List<FamilyMessage> messages = familyMessageRepository.findByFamilyId(
                familyId, category, offset, size);

        // 批量查询当前用户的点赞状态
        List<Long> messageIds = messages.stream()
                .map(FamilyMessage::getId)
                .collect(Collectors.toList());
        Set<Long> likedMessageIds = familyMessageRepository.findLikedMessageIds(messageIds, currentUserId);

        List<MessageVO> voList = messages.stream().map(msg -> {
            MessageVO vo = new MessageVO();
            vo.setId(msg.getId());
            vo.setUserId(msg.getUserId());
            vo.setUsername(msg.getUsername());
            vo.setContent(msg.getContent());
            vo.setCreateTime(msg.getCreateTime());
            vo.setOwn(Objects.equals(msg.getUserId(), currentUserId));
            vo.setLikeCount(msg.getLikeCount() != null ? msg.getLikeCount() : 0L);
            vo.setLiked(likedMessageIds.contains(msg.getId()));
            vo.setCategory(msg.getCategory());
            vo.setCategoryDesc(getCategoryDesc(msg.getCategory()));
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(voList, total, page, size);
    }

    /**
     * 获取分类描述
     *
     * @param category 分类编码
     * @return 分类描述
     */
    private String getCategoryDesc(String category) {
        if (category == null) {
            return MessageCategoryEnum.GENERAL.getDescription();
        }
        try {
            return MessageCategoryEnum.fromCode(category).getDescription();
        } catch (BusinessException e) {
            return MessageCategoryEnum.GENERAL.getDescription();
        }
    }

    /**
     * 删除留言
     *
     * @param messageId 留言ID
     * @param userId    操作者用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMessage(Long messageId, Long userId) {
        FamilyMessage message = familyMessageRepository.findById(messageId);
        if (message == null) {
            throw new BusinessException("留言不存在");
        }
        if (!Objects.equals(message.getUserId(), userId)) {
            throw new BusinessException("只能删除自己的留言");
        }

        familyMessageRepository.removeById(messageId);
        logger.info("用户 {} 删除留言: id={}", userId, messageId);
    }

    /**
     * 点赞留言
     *
     * @param messageId 留言ID
     * @param userId    用户ID
     * @param familyId  家族ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void likeMessage(Long messageId, Long userId, Long familyId) {
        FamilyMessage message = familyMessageRepository.findById(messageId);
        if (message == null) {
            throw new BusinessException("留言不存在");
        }
        if (familyMessageRepository.existsByMessageIdAndUserId(messageId, userId)) {
            throw new BusinessException("已点赞，不能重复点赞");
        }

        familyMessageRepository.saveLike(messageId, userId, familyId);
        familyMessageRepository.incrementLikeCount(messageId);
        logger.info("用户 {} 点赞留言: id={}", userId, messageId);
    }

    /**
     * 取消点赞
     *
     * @param messageId 留言ID
     * @param userId    用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlikeMessage(Long messageId, Long userId) {
        FamilyMessage message = familyMessageRepository.findById(messageId);
        if (message == null) {
            throw new BusinessException("留言不存在");
        }
        if (!familyMessageRepository.existsByMessageIdAndUserId(messageId, userId)) {
            throw new BusinessException("未点赞，不能取消点赞");
        }

        familyMessageRepository.removeLike(messageId, userId);
        familyMessageRepository.decrementLikeCount(messageId);
        logger.info("用户 {} 取消点赞留言: id={}", userId, messageId);
    }
}
