package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.ForumReplyVO;
import com.mouhin.family.tree.common.dto.ForumTopicDTO;
import com.mouhin.family.tree.common.dto.ForumTopicVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.util.HtmlSanitizeUtils;
import com.mouhin.family.tree.domain.entity.ForumReply;
import com.mouhin.family.tree.domain.entity.ForumTopic;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.ForumRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家族论坛应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class ForumApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ForumApplicationService.class);

    /**
     * 内容摘要最大长度
     */
    private static final int SUMMARY_MAX_LENGTH = 120;

    private final ForumRepository forumRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ForumApplicationService(ForumRepository forumRepository,
                                   ApplicationEventPublisher eventPublisher) {
        this.forumRepository = forumRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布主题（富文本内容入库前清洗）
     *
     * @param familyId 家族ID
     * @param userId   发帖用户ID
     * @param username 发帖用户名
     * @param dto      主题内容
     * @return 主题ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long postTopic(Long familyId, Long userId, String username, ForumTopicDTO dto) {
        if (dto.getContent() != null && dto.getContent().length() > HtmlSanitizeUtils.MAX_RICH_TEXT_LENGTH) {
            throw new BusinessException("内容过长，请精简后发布");
        }
        String content = HtmlSanitizeUtils.sanitize(dto.getContent());
        if (content == null || content.isBlank()) {
            throw new BusinessException("内容不能为空");
        }

        ForumTopic topic = new ForumTopic();
        topic.setFamilyId(familyId);
        topic.setUserId(userId);
        topic.setUsername(username);
        topic.setTitle(dto.getTitle().trim());
        topic.setContent(content);
        topic.setViewCount(0L);
        topic.setReplyCount(0L);
        topic.setCreateTime(LocalDateTime.now());
        topic.setUpdateTime(LocalDateTime.now());
        topic.validateForCreate();
        forumRepository.saveTopic(topic);
        logger.info("用户 {} 在家族 {} 发布论坛主题: id={}", userId, familyId, topic.getId());
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "FORUM_POST",
                "发布论坛主题: " + topic.getTitle(), "forum_topic", topic.getId(), familyId, null));
        return topic.getId();
    }

    /**
     * 分页查询主题列表（内容以纯文本摘要返回）
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID
     * @param page          页码（从1开始）
     * @param size          每页大小
     * @return 分页结果
     */
    public PageResult<ForumTopicVO> listTopics(Long familyId, Long currentUserId, int page, int size) {
        long total = forumRepository.countTopicsByFamilyId(familyId);
        if (total == 0) {
            return new PageResult<>(List.of(), 0L, page, size);
        }
        int offset = (page - 1) * size;
        List<ForumTopic> topics = forumRepository.findTopicsByFamilyId(familyId, offset, size);
        List<ForumTopicVO> voList = topics.stream()
                .map(topic -> toTopicVO(topic, currentUserId, false))
                .collect(Collectors.toList());
        return new PageResult<>(voList, total, page, size);
    }

    /**
     * 查询主题详情（含回复列表，浏览数加一）
     *
     * @param familyId      家族ID
     * @param topicId       主题ID
     * @param currentUserId 当前用户ID
     * @return 主题详情
     */
    @Transactional(rollbackFor = Exception.class)
    public ForumTopicVO getTopic(Long familyId, Long topicId, Long currentUserId) {
        ForumTopic topic = forumRepository.findTopicById(topicId);
        if (topic == null || !Objects.equals(topic.getFamilyId(), familyId)) {
            throw new BusinessException("主题不存在");
        }
        forumRepository.incrementViewCount(topicId);
        topic.setViewCount(topic.getViewCount() != null ? topic.getViewCount() + 1 : 1L);

        ForumTopicVO vo = toTopicVO(topic, currentUserId, true);
        List<ForumReply> replies = forumRepository.findRepliesByTopicId(topicId);
        vo.setReplies(replies.stream()
                .map(reply -> toReplyVO(reply, currentUserId))
                .collect(Collectors.toList()));
        return vo;
    }

    /**
     * 回复主题
     *
     * @param familyId      家族ID
     * @param topicId       主题ID
     * @param userId        回复用户ID
     * @param username      回复用户名
     * @param reply         回复内容（content 字段）
     * @return 回复展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public ForumReplyVO replyTopic(Long familyId, Long topicId, Long userId, String username, ForumReplyVO reply) {
        ForumTopic topic = forumRepository.findTopicById(topicId);
        if (topic == null || !Objects.equals(topic.getFamilyId(), familyId)) {
            throw new BusinessException("主题不存在");
        }
        if (reply.getContent() == null || reply.getContent().isBlank()) {
            throw new BusinessException("回复内容不能为空");
        }

        ForumReply domain = new ForumReply();
        domain.setTopicId(topicId);
        domain.setFamilyId(topic.getFamilyId());
        domain.setUserId(userId);
        domain.setUsername(username);
        domain.setContent(reply.getContent().trim());
        domain.setCreateTime(LocalDateTime.now());
        domain.validateContent();
        forumRepository.saveReply(domain);
        forumRepository.incrementReplyCount(topicId);
        logger.info("用户 {} 回复主题 {}: replyId={}", userId, topicId, domain.getId());
        return toReplyVO(domain, userId);
    }

    /**
     * 删除主题（仅发帖人可删除，同时删除全部回复）
     *
     * @param familyId 家族ID
     * @param topicId  主题ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTopic(Long familyId, Long topicId, Long userId) {
        ForumTopic topic = forumRepository.findTopicById(topicId);
        if (topic == null || !Objects.equals(topic.getFamilyId(), familyId)) {
            throw new BusinessException("主题不存在");
        }
        if (!topic.isAuthor(userId)) {
            throw new BusinessException("只能删除自己发布的主题");
        }
        forumRepository.removeRepliesByTopicId(topicId);
        forumRepository.removeTopicById(topicId);
        logger.info("用户 {} 删除主题: id={}", userId, topicId);
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, topic.getUsername(), "FORUM_DELETE",
                "删除论坛主题: " + topic.getTitle(), "forum_topic", topicId, familyId, null));
    }

    /**
     * 删除回复（仅回复人可删除，主题回复数减一）
     *
     * @param familyId 家族ID
     * @param replyId  回复ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long familyId, Long replyId, Long userId) {
        ForumReply reply = forumRepository.findReplyById(replyId);
        if (reply == null || !Objects.equals(reply.getFamilyId(), familyId)) {
            throw new BusinessException("回复不存在");
        }
        if (!reply.isAuthor(userId)) {
            throw new BusinessException("只能删除自己的回复");
        }
        forumRepository.removeReplyById(replyId);
        forumRepository.decrementReplyCount(reply.getTopicId());
        logger.info("用户 {} 删除回复: id={}, topicId={}", userId, replyId, reply.getTopicId());
    }

    /**
     * 主题领域对象转换为展示对象
     *
     * @param withDetail true 时返回富文本内容，否则返回纯文本摘要
     */
    private ForumTopicVO toTopicVO(ForumTopic topic, Long currentUserId, boolean withDetail) {
        ForumTopicVO vo = new ForumTopicVO();
        vo.setId(topic.getId());
        vo.setUserId(topic.getUserId());
        vo.setUsername(topic.getUsername());
        vo.setTitle(topic.getTitle());
        vo.setViewCount(topic.getViewCount() != null ? topic.getViewCount() : 0L);
        vo.setReplyCount(topic.getReplyCount() != null ? topic.getReplyCount() : 0L);
        vo.setCreateTime(topic.getCreateTime());
        vo.setOwn(Objects.equals(topic.getUserId(), currentUserId));
        if (withDetail) {
            vo.setContent(topic.getContent());
        } else {
            vo.setSummary(buildSummary(topic.getContent()));
        }
        return vo;
    }

    private ForumReplyVO toReplyVO(ForumReply reply, Long currentUserId) {
        ForumReplyVO vo = new ForumReplyVO();
        vo.setId(reply.getId());
        vo.setTopicId(reply.getTopicId());
        vo.setUserId(reply.getUserId());
        vo.setUsername(reply.getUsername());
        vo.setContent(reply.getContent());
        vo.setCreateTime(reply.getCreateTime());
        vo.setOwn(Objects.equals(reply.getUserId(), currentUserId));
        return vo;
    }

    /**
     * 提取纯文本摘要
     */
    private String buildSummary(String html) {
        String text = HtmlSanitizeUtils.extractText(html);
        if (text == null) {
            return "";
        }
        if (text.length() <= SUMMARY_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, SUMMARY_MAX_LENGTH) + "...";
    }
}
