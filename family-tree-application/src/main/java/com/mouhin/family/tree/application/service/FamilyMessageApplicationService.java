package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.MessageCreateDTO;
import com.mouhin.family.tree.common.dto.MessageVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.enums.MessageCategoryEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyMessage;
import com.mouhin.family.tree.domain.event.FamilyMessageDeletedEvent;
import com.mouhin.family.tree.domain.event.FamilyMessagePostedEvent;
import com.mouhin.family.tree.domain.repository.FamilyMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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

    /**
     * 留言内容最大长度
     */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final FamilyMessageRepository familyMessageRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FamilyMessageApplicationService(FamilyMessageRepository familyMessageRepository,
                                           ApplicationEventPublisher eventPublisher) {
        this.familyMessageRepository = familyMessageRepository;
        this.eventPublisher = eventPublisher;
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

        // 回复校验：父留言必须存在且为顶级留言
        Long parentId = dto.getParentId();
        if (parentId != null) {
            FamilyMessage parent = familyMessageRepository.findById(parentId);
            if (parent == null) {
                throw new BusinessException("被回复的留言不存在");
            }
            if (!parent.isRootMessage()) {
                throw new BusinessException("只能回复顶级留言");
            }
            // 回复继承父留言的家族ID
            familyId = parent.getFamilyId();
        }

        FamilyMessage message = new FamilyMessage();
        message.setFamilyId(familyId);
        message.setUserId(userId);
        message.setUsername(username != null ? username : "匿名");
        message.setContent(content.trim());
        message.setLikeCount(0L);
        message.setCategory(category);
        message.setParentId(parentId);
        message.setReplyCount(0L);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());

        familyMessageRepository.save(message);

        // 更新父留言的回复计数
        if (parentId != null) {
            familyMessageRepository.incrementReplyCount(parentId);
        }

        logger.info("用户 {} 在家族 {} 发布留言: id={}, parentId={}, category={}", userId, familyId, message.getId(), parentId, category);
        eventPublisher.publishEvent(FamilyMessagePostedEvent.of(familyId, message.getId(), userId, parentId));
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

        // 批量查询当前用户的点赞状态（含顶级留言和所有回复）
        List<Long> messageIds = messages.stream()
                .map(FamilyMessage::getId)
                .collect(Collectors.toList());
        Set<Long> likedMessageIds = familyMessageRepository.findLikedMessageIds(messageIds, currentUserId);

        List<MessageVO> voList = messages.stream().map(msg -> {
            MessageVO vo = toMessageVO(msg, currentUserId, likedMessageIds);
            // 加载回复列表
            if (msg.getReplyCount() != null && msg.getReplyCount() > 0) {
                List<FamilyMessage> replies = familyMessageRepository.findByParentId(msg.getId());
                // 收集回复的ID用于批量查点赞
                List<Long> replyIds = replies.stream()
                        .map(FamilyMessage::getId)
                        .collect(Collectors.toList());
                Set<Long> likedReplyIds = familyMessageRepository.findLikedMessageIds(replyIds, currentUserId);
                List<MessageVO> replyVOs = replies.stream()
                        .map(r -> toMessageVO(r, currentUserId, likedReplyIds))
                        .collect(Collectors.toList());
                vo.setReplies(replyVOs);
            } else {
                vo.setReplies(List.of());
            }
            return vo;
        }).collect(Collectors.toList());

        return new PageResult<>(voList, total, page, size);
    }

    /**
     * 查询指定留言的回复列表
     *
     * @param messageId 留言ID
     * @param userId    当前用户ID
     * @return 回复列表
     */
    public List<MessageVO> listReplies(Long messageId, Long userId) {
        FamilyMessage parent = familyMessageRepository.findById(messageId);
        if (parent == null) {
            throw new BusinessException("留言不存在");
        }
        List<FamilyMessage> replies = familyMessageRepository.findByParentId(messageId);
        if (replies.isEmpty()) {
            return List.of();
        }
        List<Long> replyIds = replies.stream().map(FamilyMessage::getId).collect(Collectors.toList());
        Set<Long> likedReplyIds = familyMessageRepository.findLikedMessageIds(replyIds, userId);
        return replies.stream()
                .map(r -> toMessageVO(r, userId, likedReplyIds))
                .collect(Collectors.toList());
    }

    /**
     * 将领域对象转换为展示对象
     */
    private MessageVO toMessageVO(FamilyMessage msg, Long currentUserId, Set<Long> likedIds) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setUserId(msg.getUserId());
        vo.setUsername(msg.getUsername());
        vo.setContent(msg.getContent());
        vo.setCreateTime(msg.getCreateTime());
        vo.setOwn(Objects.equals(msg.getUserId(), currentUserId));
        vo.setLikeCount(msg.getLikeCount() != null ? msg.getLikeCount() : 0L);
        vo.setLiked(likedIds.contains(msg.getId()));
        vo.setCategory(msg.getCategory());
        vo.setCategoryDesc(getCategoryDesc(msg.getCategory()));
        vo.setParentId(msg.getParentId());
        vo.setReplyCount(msg.getReplyCount() != null ? msg.getReplyCount() : 0L);
        return vo;
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

        // 删除顶级留言时，级联删除所有回复
        if (message.isRootMessage()) {
            familyMessageRepository.removeByParentId(messageId);
        } else {
            // 删除回复时，减少父留言的回复计数
            familyMessageRepository.decrementReplyCount(message.getParentId());
        }

        familyMessageRepository.removeById(messageId);
        logger.info("用户 {} 删除留言: id={}", userId, messageId);
        eventPublisher.publishEvent(FamilyMessageDeletedEvent.of(message.getFamilyId(), messageId, userId));
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
