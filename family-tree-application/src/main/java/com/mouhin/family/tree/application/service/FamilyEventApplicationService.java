package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.EventDTO;
import com.mouhin.family.tree.common.dto.EventSignupVO;
import com.mouhin.family.tree.common.dto.EventVO;
import com.mouhin.family.tree.common.enums.EventStatusEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.EventSignup;
import com.mouhin.family.tree.domain.entity.FamilyEvent;
import com.mouhin.family.tree.domain.event.OperationPerformedEvent;
import com.mouhin.family.tree.domain.repository.FamilyEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 家族活动组织应用服务
 *
 * @author Family-Tree
 * @date 2026-08-30
 */
@Service
public class FamilyEventApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(FamilyEventApplicationService.class);

    /**
     * 活动时间格式（兼容 datetime-local 控件的 T 分隔符）
     */
    private static final DateTimeFormatter EVENT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

    private final FamilyEventRepository familyEventRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FamilyEventApplicationService(FamilyEventRepository familyEventRepository,
                                         ApplicationEventPublisher eventPublisher) {
        this.familyEventRepository = familyEventRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发起活动
     *
     * @param familyId 家族ID
     * @param userId   发起人用户ID
     * @param username 发起人用户名
     * @param dto      活动内容
     * @return 活动ID
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createEvent(Long familyId, Long userId, String username, EventDTO dto) {
        FamilyEvent event = new FamilyEvent();
        event.setFamilyId(familyId);
        event.setUserId(userId);
        event.setUsername(username);
        event.setTitle(dto.getTitle() != null ? dto.getTitle().trim() : null);
        event.setDescription(dto.getDescription());
        event.setEventTime(parseEventTime(dto.getEventTime()));
        event.setLocation(dto.getLocation());
        event.setTotalCost(dto.getTotalCost());
        event.setStatus(EventStatusEnum.OPEN.getCode());
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());
        event.validateForCreate();
        familyEventRepository.save(event);
        logger.info("用户 {} 在家族 {} 发起活动: id={}", userId, familyId, event.getId());
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, username, "EVENT_CREATE",
                "发起家族活动: " + event.getTitle(), "event", event.getId(), familyId, null));
        return event.getId();
    }

    /**
     * 查询家族活动列表（含报名汇总与人均费用）
     *
     * @param familyId      家族ID
     * @param currentUserId 当前用户ID
     * @return 活动列表
     */
    public List<EventVO> listEvents(Long familyId, Long currentUserId) {
        List<FamilyEvent> events = familyEventRepository.findByFamilyId(familyId);
        return events.stream()
                .map(event -> toEventVO(event, currentUserId, false))
                .collect(Collectors.toList());
    }

    /**
     * 查询活动详情（含报名明细）
     *
     * @param familyId      家族ID
     * @param eventId       活动ID
     * @param currentUserId 当前用户ID
     * @return 活动详情
     */
    public EventVO getEvent(Long familyId, Long eventId, Long currentUserId) {
        FamilyEvent event = getEventChecked(familyId, eventId);
        return toEventVO(event, currentUserId, true);
    }

    /**
     * 报名活动
     *
     * @param familyId      家族ID
     * @param eventId       活动ID
     * @param userId        报名用户ID
     * @param username      报名用户名
     * @param signupRequest 报名信息（人数、备注）
     * @return 报名展示对象
     */
    @Transactional(rollbackFor = Exception.class)
    public EventSignupVO signup(Long familyId, Long eventId, Long userId, String username,
                                EventSignupVO signupRequest) {
        FamilyEvent event = getEventChecked(familyId, eventId);
        if (!event.isOpen()) {
            throw new BusinessException("活动报名已截止");
        }
        if (familyEventRepository.findSignup(eventId, userId) != null) {
            throw new BusinessException("您已报名该活动，请先取消再重新报名");
        }

        EventSignup signup = new EventSignup();
        signup.setEventId(eventId);
        signup.setFamilyId(event.getFamilyId());
        signup.setUserId(userId);
        signup.setUsername(username);
        signup.setAttendeeCount(signupRequest != null && signupRequest.getAttendeeCount() != null
                ? signupRequest.getAttendeeCount() : 1);
        signup.setRemark(signupRequest != null ? signupRequest.getRemark() : null);
        signup.setCreateTime(LocalDateTime.now());
        signup.validateForCreate();
        familyEventRepository.saveSignup(signup);
        logger.info("用户 {} 报名活动 {}: signupId={}, attendeeCount={}",
                userId, eventId, signup.getId(), signup.getAttendeeCount());
        return toSignupVO(signup);
    }

    /**
     * 取消报名
     *
     * @param familyId 家族ID
     * @param eventId  活动ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelSignup(Long familyId, Long eventId, Long userId) {
        getEventChecked(familyId, eventId);
        EventSignup signup = familyEventRepository.findSignup(eventId, userId);
        if (signup == null) {
            throw new BusinessException("您未报名该活动");
        }
        familyEventRepository.removeSignupById(signup.getId());
        logger.info("用户 {} 取消活动 {} 的报名", userId, eventId);
    }

    /**
     * 切换活动报名状态（仅发起人可操作）
     *
     * @param familyId 家族ID
     * @param eventId  活动ID
     * @param userId   当前用户ID
     * @param open     true 为开放报名，false 为截止
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long familyId, Long eventId, Long userId, boolean open) {
        FamilyEvent event = getEventChecked(familyId, eventId);
        if (!event.isCreator(userId)) {
            throw new BusinessException("只有活动发起人可以修改报名状态");
        }
        event.setStatus(open ? EventStatusEnum.OPEN.getCode() : EventStatusEnum.CLOSED.getCode());
        event.setUpdateTime(LocalDateTime.now());
        familyEventRepository.update(event);
        logger.info("用户 {} 更新活动 {} 状态为: {}", userId, eventId, event.getStatus());
    }

    /**
     * 删除活动（仅发起人可删除，同时删除全部报名记录）
     *
     * @param familyId 家族ID
     * @param eventId  活动ID
     * @param userId   当前用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteEvent(Long familyId, Long eventId, Long userId) {
        FamilyEvent event = getEventChecked(familyId, eventId);
        if (!event.isCreator(userId)) {
            throw new BusinessException("只有活动发起人可以删除活动");
        }
        familyEventRepository.removeSignupsByEventId(eventId);
        familyEventRepository.removeById(eventId);
        logger.info("用户 {} 删除活动: id={}", userId, eventId);
        eventPublisher.publishEvent(OperationPerformedEvent.of(userId, event.getUsername(), "EVENT_DELETE",
                "删除家族活动: " + event.getTitle(), "event", eventId, familyId, null));
    }

    private FamilyEvent getEventChecked(Long familyId, Long eventId) {
        FamilyEvent event = familyEventRepository.findById(eventId);
        if (event == null || !Objects.equals(event.getFamilyId(), familyId)) {
            throw new BusinessException("活动不存在");
        }
        return event;
    }

    private EventVO toEventVO(FamilyEvent event, Long currentUserId, boolean withSignups) {
        EventVO vo = new EventVO();
        vo.setId(event.getId());
        vo.setUserId(event.getUserId());
        vo.setUsername(event.getUsername());
        vo.setTitle(event.getTitle());
        vo.setDescription(event.getDescription());
        vo.setEventTime(event.getEventTime());
        vo.setLocation(event.getLocation());
        vo.setTotalCost(event.getTotalCost());
        vo.setStatus(event.getStatus());
        vo.setStatusDesc(getStatusDesc(event.getStatus()));
        vo.setOwn(event.isCreator(currentUserId));
        vo.setCreateTime(event.getCreateTime());

        List<EventSignup> signups = familyEventRepository.findSignupsByEventId(event.getId());
        int totalAttendees = signups.stream()
                .mapToInt(s -> s.getAttendeeCount() != null ? s.getAttendeeCount() : 0)
                .sum();
        vo.setTotalAttendees(totalAttendees);
        vo.setPerPersonCost(calculatePerPersonCost(event.getTotalCost(), totalAttendees));
        vo.setSignedUp(signups.stream().anyMatch(s -> Objects.equals(s.getUserId(), currentUserId)));
        if (withSignups) {
            vo.setSignups(signups.stream().map(this::toSignupVO).collect(Collectors.toList()));
        }
        return vo;
    }

    private EventSignupVO toSignupVO(EventSignup signup) {
        EventSignupVO vo = new EventSignupVO();
        vo.setId(signup.getId());
        vo.setUserId(signup.getUserId());
        vo.setUsername(signup.getUsername());
        vo.setAttendeeCount(signup.getAttendeeCount());
        vo.setRemark(signup.getRemark());
        vo.setCreateTime(signup.getCreateTime());
        return vo;
    }

    /**
     * 人均费用 = 总费用 / 总人数（四舍五入保留两位小数）
     */
    private BigDecimal calculatePerPersonCost(BigDecimal totalCost, int totalAttendees) {
        if (totalCost == null || totalCost.compareTo(BigDecimal.ZERO) <= 0 || totalAttendees == 0) {
            return null;
        }
        return totalCost.divide(BigDecimal.valueOf(totalAttendees), 2, RoundingMode.HALF_UP);
    }

    private String getStatusDesc(String status) {
        for (EventStatusEnum statusEnum : EventStatusEnum.values()) {
            if (statusEnum.getCode().equals(status)) {
                return statusEnum.getDescription();
            }
        }
        return status;
    }

    private LocalDateTime parseEventTime(String eventTime) {
        if (eventTime == null || eventTime.isBlank()) {
            throw new BusinessException("请填写活动时间");
        }
        String normalized = eventTime.trim().replace('T', ' ');
        try {
            return LocalDateTime.parse(normalized, EVENT_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException("活动时间格式错误，请使用 yyyy-MM-dd HH:mm 格式");
        }
    }
}
