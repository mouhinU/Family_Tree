package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.ForumReplyVO;
import com.mouhin.family.tree.common.dto.ForumTopicDTO;
import com.mouhin.family.tree.common.dto.ForumTopicVO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.ForumReply;
import com.mouhin.family.tree.domain.entity.ForumTopic;
import com.mouhin.family.tree.domain.repository.ForumRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 家族论坛应用服务单元测试。
 * 覆盖：发布主题（正常/清洗脚本/空内容/超长）、分页查询（空/摘要截断）、
 * 主题详情（浏览计数/回复列表/不存在）、回复主题（正常/空内容/主题不存在）、
 * 删除主题（正常/非作者/级联删除回复）、删除回复（正常/非作者/回复数减一）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class ForumApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;
    private static final Long TOPIC_ID = 10L;

    @Mock
    private ForumRepository forumRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ForumApplicationService forumApplicationService;

    // ========== 发布主题 ==========

    @Test
    void postTopic_success_stripsScript() {
        ForumTopicDTO dto = new ForumTopicDTO();
        dto.setTitle("  春节聚会讨论  ");
        dto.setContent("<p>正文内容</p><script>alert(1)</script>");

        when(forumRepository.saveTopic(any(ForumTopic.class))).thenAnswer(invocation -> {
            ForumTopic topic = invocation.getArgument(0);
            topic.setId(TOPIC_ID);
            return topic;
        });

        Long topicId = forumApplicationService.postTopic(FAMILY_ID, USER_ID, "测试用户", dto);

        assertEquals(TOPIC_ID, topicId);
        ArgumentCaptor<ForumTopic> captor = ArgumentCaptor.forClass(ForumTopic.class);
        verify(forumRepository).saveTopic(captor.capture());
        ForumTopic saved = captor.getValue();
        assertEquals("春节聚会讨论", saved.getTitle());
        assertFalse(saved.getContent().contains("script"));
        assertTrue(saved.getContent().contains("正文内容"));
        assertEquals(0L, saved.getViewCount());
        assertEquals(0L, saved.getReplyCount());
    }

    @Test
    void postTopic_blankContent_throws() {
        ForumTopicDTO dto = new ForumTopicDTO();
        dto.setTitle("标题");
        dto.setContent("   ");

        assertThrows(BusinessException.class, () ->
                forumApplicationService.postTopic(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(forumRepository, never()).saveTopic(any(ForumTopic.class));
    }

    @Test
    void postTopic_scriptOnlyContent_throws() {
        ForumTopicDTO dto = new ForumTopicDTO();
        dto.setTitle("标题");
        dto.setContent("<script>alert(1)</script>");

        assertThrows(BusinessException.class, () ->
                forumApplicationService.postTopic(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(forumRepository, never()).saveTopic(any(ForumTopic.class));
    }

    // ========== 分页查询 ==========

    @Test
    void listTopics_empty() {
        when(forumRepository.countTopicsByFamilyId(FAMILY_ID)).thenReturn(0L);

        PageResult<ForumTopicVO> result = forumApplicationService.listTopics(FAMILY_ID, USER_ID, 1, 20);

        assertEquals(0L, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verify(forumRepository, never()).findTopicsByFamilyId(anyLong(), anyInt(), anyInt());
    }

    @Test
    void listTopics_summaryAndOwnFlag() {
        when(forumRepository.countTopicsByFamilyId(FAMILY_ID)).thenReturn(2L);

        ForumTopic mine = buildTopic(1L, USER_ID, "长内容".repeat(60));
        ForumTopic others = buildTopic(2L, OTHER_USER_ID, "<p>短内容</p>");
        when(forumRepository.findTopicsByFamilyId(FAMILY_ID, 0, 20)).thenReturn(List.of(mine, others));

        PageResult<ForumTopicVO> result = forumApplicationService.listTopics(FAMILY_ID, USER_ID, 1, 20);

        assertEquals(2L, result.getTotal());
        ForumTopicVO first = result.getRecords().get(0);
        assertTrue(first.getOwn());
        assertTrue(first.getSummary().endsWith("..."));
        assertTrue(first.getSummary().length() <= 123);
        assertNull(first.getContent());

        ForumTopicVO second = result.getRecords().get(1);
        assertFalse(second.getOwn());
        assertEquals("短内容", second.getSummary());
    }

    // ========== 主题详情 ==========

    @Test
    void getTopic_success_withReplies() {
        ForumTopic topic = buildTopic(TOPIC_ID, USER_ID, "<p>详情内容</p>");
        topic.setViewCount(5L);
        when(forumRepository.findTopicById(TOPIC_ID)).thenReturn(topic);

        ForumReply reply = buildReply(20L, TOPIC_ID, OTHER_USER_ID, "回复内容");
        when(forumRepository.findRepliesByTopicId(TOPIC_ID)).thenReturn(List.of(reply));

        ForumTopicVO vo = forumApplicationService.getTopic(FAMILY_ID, TOPIC_ID, USER_ID);

        verify(forumRepository).incrementViewCount(TOPIC_ID);
        assertEquals(6L, vo.getViewCount());
        assertEquals("<p>详情内容</p>", vo.getContent());
        assertEquals(1, vo.getReplies().size());
        assertEquals("回复内容", vo.getReplies().get(0).getContent());
        assertFalse(vo.getReplies().get(0).getOwn());
    }

    @Test
    void getTopic_notFound_throws() {
        when(forumRepository.findTopicById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                forumApplicationService.getTopic(FAMILY_ID, 999L, USER_ID));
    }

    // ========== 回复主题 ==========

    @Test
    void replyTopic_success() {
        ForumTopic topic = buildTopic(TOPIC_ID, USER_ID, "<p>详情</p>");
        when(forumRepository.findTopicById(TOPIC_ID)).thenReturn(topic);

        ForumReplyVO replyRequest = new ForumReplyVO();
        replyRequest.setContent("  同意  ");

        ForumReplyVO vo = forumApplicationService.replyTopic(
                FAMILY_ID, TOPIC_ID, OTHER_USER_ID, "用户B", replyRequest);

        ArgumentCaptor<ForumReply> captor = ArgumentCaptor.forClass(ForumReply.class);
        verify(forumRepository).saveReply(captor.capture());
        ForumReply saved = captor.getValue();
        assertEquals(TOPIC_ID, saved.getTopicId());
        assertEquals(FAMILY_ID, saved.getFamilyId());
        assertEquals("同意", saved.getContent());
        verify(forumRepository).incrementReplyCount(TOPIC_ID);
        assertEquals("同意", vo.getContent());
        assertTrue(vo.getOwn());
    }

    @Test
    void replyTopic_blankContent_throws() {
        ForumTopic topic = buildTopic(TOPIC_ID, USER_ID, "<p>详情</p>");
        when(forumRepository.findTopicById(TOPIC_ID)).thenReturn(topic);

        ForumReplyVO replyRequest = new ForumReplyVO();
        replyRequest.setContent("  ");

        assertThrows(BusinessException.class, () ->
                forumApplicationService.replyTopic(FAMILY_ID, TOPIC_ID, USER_ID, "测试用户", replyRequest));
        verify(forumRepository, never()).saveReply(any(ForumReply.class));
    }

    // ========== 删除主题 ==========

    @Test
    void deleteTopic_success_cascadeReplies() {
        ForumTopic topic = buildTopic(TOPIC_ID, USER_ID, "<p>详情</p>");
        when(forumRepository.findTopicById(TOPIC_ID)).thenReturn(topic);

        forumApplicationService.deleteTopic(FAMILY_ID, TOPIC_ID, USER_ID);

        verify(forumRepository).removeRepliesByTopicId(TOPIC_ID);
        verify(forumRepository).removeTopicById(TOPIC_ID);
    }

    @Test
    void deleteTopic_notAuthor_throws() {
        ForumTopic topic = buildTopic(TOPIC_ID, OTHER_USER_ID, "<p>详情</p>");
        when(forumRepository.findTopicById(TOPIC_ID)).thenReturn(topic);

        assertThrows(BusinessException.class, () ->
                forumApplicationService.deleteTopic(FAMILY_ID, TOPIC_ID, USER_ID));
        verify(forumRepository, never()).removeTopicById(any());
    }

    // ========== 删除回复 ==========

    @Test
    void deleteReply_success_decrementCount() {
        ForumReply reply = buildReply(20L, TOPIC_ID, USER_ID, "我的回复");
        when(forumRepository.findReplyById(20L)).thenReturn(reply);

        forumApplicationService.deleteReply(FAMILY_ID, 20L, USER_ID);

        verify(forumRepository).removeReplyById(20L);
        verify(forumRepository).decrementReplyCount(TOPIC_ID);
    }

    @Test
    void deleteReply_notAuthor_throws() {
        ForumReply reply = buildReply(20L, TOPIC_ID, OTHER_USER_ID, "别人的回复");
        when(forumRepository.findReplyById(20L)).thenReturn(reply);

        assertThrows(BusinessException.class, () ->
                forumApplicationService.deleteReply(FAMILY_ID, 20L, USER_ID));
        verify(forumRepository, never()).removeReplyById(any());
    }

    // ========== 辅助方法 ==========

    private ForumTopic buildTopic(Long id, Long userId, String content) {
        ForumTopic topic = new ForumTopic();
        topic.setId(id);
        topic.setFamilyId(FAMILY_ID);
        topic.setUserId(userId);
        topic.setUsername("用户" + userId);
        topic.setTitle("主题" + id);
        topic.setContent(content);
        topic.setViewCount(0L);
        topic.setReplyCount(0L);
        topic.setCreateTime(LocalDateTime.now());
        topic.setUpdateTime(LocalDateTime.now());
        return topic;
    }

    private ForumReply buildReply(Long id, Long topicId, Long userId, String content) {
        ForumReply reply = new ForumReply();
        reply.setId(id);
        reply.setTopicId(topicId);
        reply.setFamilyId(FAMILY_ID);
        reply.setUserId(userId);
        reply.setUsername("用户" + userId);
        reply.setContent(content);
        reply.setCreateTime(LocalDateTime.now());
        return reply;
    }
}
