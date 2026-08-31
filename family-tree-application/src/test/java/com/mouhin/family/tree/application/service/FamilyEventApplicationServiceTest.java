package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.EventDTO;
import com.mouhin.family.tree.common.dto.EventSignupVO;
import com.mouhin.family.tree.common.dto.EventVO;
import com.mouhin.family.tree.common.enums.EventStatusEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.EventSignup;
import com.mouhin.family.tree.domain.entity.FamilyEvent;
import com.mouhin.family.tree.domain.repository.FamilyEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 家族活动组织应用服务单元测试。
 * 覆盖：发起活动（正常/时间格式/缺时间）、活动列表（人数汇总/人均费用/已报名标记）、
 * 活动详情（报名明细/不存在）、报名（正常/默认人数/已截止/重复报名）、
 * 取消报名（正常/未报名）、状态切换（仅发起人）、删除活动（级联删除报名/非发起人）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class FamilyEventApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;
    private static final Long EVENT_ID = 10L;

    @Mock
    private FamilyEventRepository familyEventRepository;

    @Mock
    private OperationLogApplicationService operationLogService;

    @InjectMocks
    private FamilyEventApplicationService familyEventApplicationService;

    // ========== 发起活动 ==========

    @Test
    void createEvent_success_parsesTimeWithT() {
        EventDTO dto = new EventDTO();
        dto.setTitle("  春节聚会  ");
        dto.setDescription("一起吃年夜饭");
        dto.setEventTime("2026-10-01T18:30");
        dto.setLocation("老家");
        dto.setTotalCost(new BigDecimal("300.00"));

        when(familyEventRepository.save(any(FamilyEvent.class))).thenAnswer(invocation -> {
            FamilyEvent event = invocation.getArgument(0);
            event.setId(EVENT_ID);
            return event;
        });

        Long eventId = familyEventApplicationService.createEvent(FAMILY_ID, USER_ID, "测试用户", dto);

        assertEquals(EVENT_ID, eventId);
        ArgumentCaptor<FamilyEvent> captor = ArgumentCaptor.forClass(FamilyEvent.class);
        verify(familyEventRepository).save(captor.capture());
        FamilyEvent saved = captor.getValue();
        assertEquals("春节聚会", saved.getTitle());
        assertEquals(LocalDateTime.of(2026, 10, 1, 18, 30), saved.getEventTime());
        assertEquals(EventStatusEnum.OPEN.getCode(), saved.getStatus());
    }

    @Test
    void createEvent_invalidTime_throws() {
        EventDTO dto = buildEventDTO("2026/10/01 18:30");

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.createEvent(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyEventRepository, never()).save(any(FamilyEvent.class));
    }

    @Test
    void createEvent_blankTime_throws() {
        EventDTO dto = buildEventDTO("  ");

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.createEvent(FAMILY_ID, USER_ID, "测试用户", dto));
        verify(familyEventRepository, never()).save(any(FamilyEvent.class));
    }

    // ========== 活动列表 ==========

    @Test
    void listEvents_calculatesAttendeesAndPerPersonCost() {
        FamilyEvent event = buildEvent(USER_ID, EventStatusEnum.OPEN.getCode());
        event.setTotalCost(new BigDecimal("100.00"));
        when(familyEventRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(event));

        EventSignup signup1 = buildSignup(1L, USER_ID, 2);
        EventSignup signup2 = buildSignup(2L, OTHER_USER_ID, 1);
        when(familyEventRepository.findSignupsByEventId(EVENT_ID))
                .thenReturn(List.of(signup1, signup2));

        List<EventVO> events = familyEventApplicationService.listEvents(FAMILY_ID, USER_ID);

        assertEquals(1, events.size());
        EventVO vo = events.get(0);
        assertEquals(3, vo.getTotalAttendees());
        assertEquals(new BigDecimal("33.33"), vo.getPerPersonCost());
        assertTrue(vo.getOwn());
        assertTrue(vo.getSignedUp());
        assertEquals(EventStatusEnum.OPEN.getDescription(), vo.getStatusDesc());
        assertNull(vo.getSignups());
    }

    @Test
    void listEvents_noSignups_perPersonCostNull() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.CLOSED.getCode());
        event.setTotalCost(new BigDecimal("100.00"));
        when(familyEventRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(event));
        when(familyEventRepository.findSignupsByEventId(EVENT_ID)).thenReturn(List.of());

        List<EventVO> events = familyEventApplicationService.listEvents(FAMILY_ID, USER_ID);

        assertNull(events.get(0).getPerPersonCost());
        assertFalse(events.get(0).getOwn());
        assertFalse(events.get(0).getSignedUp());
        assertEquals(0, events.get(0).getTotalAttendees());
    }

    // ========== 活动详情 ==========

    @Test
    void getEvent_success_withSignups() {
        FamilyEvent event = buildEvent(USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);
        when(familyEventRepository.findSignupsByEventId(EVENT_ID))
                .thenReturn(List.of(buildSignup(1L, OTHER_USER_ID, 2)));

        EventVO vo = familyEventApplicationService.getEvent(FAMILY_ID, EVENT_ID, USER_ID);

        assertNotNull(vo.getSignups());
        assertEquals(1, vo.getSignups().size());
        assertEquals(OTHER_USER_ID, vo.getSignups().get(0).getUserId());
    }

    @Test
    void getEvent_notFound_throws() {
        when(familyEventRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.getEvent(FAMILY_ID, 999L, USER_ID));
    }

    // ========== 报名 ==========

    @Test
    void signup_success_defaultAttendeeCount() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);
        when(familyEventRepository.findSignup(EVENT_ID, USER_ID)).thenReturn(null);

        EventSignupVO vo = familyEventApplicationService.signup(
                FAMILY_ID, EVENT_ID, USER_ID, "测试用户", null);

        ArgumentCaptor<EventSignup> captor = ArgumentCaptor.forClass(EventSignup.class);
        verify(familyEventRepository).saveSignup(captor.capture());
        assertEquals(EVENT_ID, captor.getValue().getEventId());
        assertEquals(1, captor.getValue().getAttendeeCount());
        assertEquals(1, vo.getAttendeeCount());
    }

    @Test
    void signup_closedEvent_throws() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.CLOSED.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.signup(FAMILY_ID, EVENT_ID, USER_ID, "测试用户", null));
        verify(familyEventRepository, never()).saveSignup(any(EventSignup.class));
    }

    @Test
    void signup_duplicate_throws() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);
        when(familyEventRepository.findSignup(EVENT_ID, USER_ID)).thenReturn(buildSignup(1L, USER_ID, 1));

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.signup(FAMILY_ID, EVENT_ID, USER_ID, "测试用户", null));
        verify(familyEventRepository, never()).saveSignup(any(EventSignup.class));
    }

    // ========== 取消报名 ==========

    @Test
    void cancelSignup_success() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);
        when(familyEventRepository.findSignup(EVENT_ID, USER_ID)).thenReturn(buildSignup(5L, USER_ID, 1));

        familyEventApplicationService.cancelSignup(FAMILY_ID, EVENT_ID, USER_ID);

        verify(familyEventRepository).removeSignupById(5L);
    }

    @Test
    void cancelSignup_notSignedUp_throws() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);
        when(familyEventRepository.findSignup(EVENT_ID, USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.cancelSignup(FAMILY_ID, EVENT_ID, USER_ID));
        verify(familyEventRepository, never()).removeSignupById(any());
    }

    // ========== 状态切换 ==========

    @Test
    void updateStatus_creator_closes() {
        FamilyEvent event = buildEvent(USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);

        familyEventApplicationService.updateStatus(FAMILY_ID, EVENT_ID, USER_ID, false);

        ArgumentCaptor<FamilyEvent> captor = ArgumentCaptor.forClass(FamilyEvent.class);
        verify(familyEventRepository).update(captor.capture());
        assertEquals(EventStatusEnum.CLOSED.getCode(), captor.getValue().getStatus());
    }

    @Test
    void updateStatus_notCreator_throws() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.updateStatus(FAMILY_ID, EVENT_ID, USER_ID, false));
        verify(familyEventRepository, never()).update(any(FamilyEvent.class));
    }

    // ========== 删除活动 ==========

    @Test
    void deleteEvent_success_cascadeSignups() {
        FamilyEvent event = buildEvent(USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);

        familyEventApplicationService.deleteEvent(FAMILY_ID, EVENT_ID, USER_ID);

        verify(familyEventRepository).removeSignupsByEventId(EVENT_ID);
        verify(familyEventRepository).removeById(EVENT_ID);
    }

    @Test
    void deleteEvent_notCreator_throws() {
        FamilyEvent event = buildEvent(OTHER_USER_ID, EventStatusEnum.OPEN.getCode());
        when(familyEventRepository.findById(EVENT_ID)).thenReturn(event);

        assertThrows(BusinessException.class, () ->
                familyEventApplicationService.deleteEvent(FAMILY_ID, EVENT_ID, USER_ID));
        verify(familyEventRepository, never()).removeById(any());
    }

    // ========== 辅助方法 ==========

    private EventDTO buildEventDTO(String eventTime) {
        EventDTO dto = new EventDTO();
        dto.setTitle("活动");
        dto.setEventTime(eventTime);
        return dto;
    }

    private FamilyEvent buildEvent(Long userId, String status) {
        FamilyEvent event = new FamilyEvent();
        event.setId(EVENT_ID);
        event.setFamilyId(FAMILY_ID);
        event.setUserId(userId);
        event.setUsername("用户" + userId);
        event.setTitle("家族聚会");
        event.setEventTime(LocalDateTime.of(2026, 10, 1, 18, 30));
        event.setStatus(status);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());
        return event;
    }

    private EventSignup buildSignup(Long id, Long userId, Integer attendeeCount) {
        EventSignup signup = new EventSignup();
        signup.setId(id);
        signup.setEventId(EVENT_ID);
        signup.setFamilyId(FAMILY_ID);
        signup.setUserId(userId);
        signup.setUsername("用户" + userId);
        signup.setAttendeeCount(attendeeCount);
        signup.setCreateTime(LocalDateTime.now());
        return signup;
    }
}
