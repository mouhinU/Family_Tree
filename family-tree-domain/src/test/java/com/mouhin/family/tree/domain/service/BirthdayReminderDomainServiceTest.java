package com.mouhin.family.tree.domain.service;

import com.mouhin.family.tree.domain.entity.FamilyNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 生日提醒领域服务单元测试。
 * 覆盖：窗口内生日命中、按剩余天数升序、已故成员排除、无生日排除、
 * 窗口外生日排除、今年已过生日顺延明年。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
class BirthdayReminderDomainServiceTest {

    private BirthdayReminderDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new BirthdayReminderDomainService();
    }

    @Test
    void calculateUpcoming_withinWindow() {
        FamilyNode node = buildNode(1L, "张三", LocalDate.now().plusDays(3).minusYears(30), false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(node), 7);

        assertEquals(1, result.size());
        BirthdayReminderDomainService.BirthdayInfo info = result.get(0);
        assertEquals(1L, info.getNodeId());
        assertEquals("张三", info.getNodeName());
        assertEquals(3L, info.getDaysUntil());
        assertEquals(30, info.getAge());
    }

    @Test
    void calculateUpcoming_sortedByDaysUntil() {
        FamilyNode far = buildNode(1L, "较远", LocalDate.now().plusDays(6).minusYears(20), false);
        FamilyNode near = buildNode(2L, "较近", LocalDate.now().plusDays(2).minusYears(25), false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(far, near), 7);

        assertEquals(2, result.size());
        assertEquals(2L, result.get(0).getNodeId());
        assertEquals(1L, result.get(1).getNodeId());
    }

    @Test
    void calculateUpcoming_deceasedExcluded() {
        FamilyNode deceased = buildNode(1L, "已故", LocalDate.now().plusDays(1).minusYears(80), true);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(deceased), 7);

        assertTrue(result.isEmpty());
    }

    @Test
    void calculateUpcoming_nullBirthDateExcluded() {
        FamilyNode node = buildNode(1L, "无生日", null, false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(node), 7);

        assertTrue(result.isEmpty());
    }

    @Test
    void calculateUpcoming_outsideWindowExcluded() {
        FamilyNode node = buildNode(1L, "窗口外", LocalDate.now().plusDays(10).minusYears(20), false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(node), 7);

        assertTrue(result.isEmpty());
    }

    @Test
    void calculateUpcoming_birthdayToday() {
        FamilyNode node = buildNode(1L, "今日寿星", LocalDate.now().minusYears(18), false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(node), 7);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getDaysUntil());
        assertEquals(18, result.get(0).getAge());
    }

    @Test
    void calculateUpcoming_passedBirthday_rollsToNextYear() {
        // 今年生日已过：下次生日在明年（窗口设为 400 天以覆盖跨年）
        LocalDate birthDate = LocalDate.now().minusDays(3).minusYears(40);
        FamilyNode node = buildNode(1L, "已过", birthDate, false);

        List<BirthdayReminderDomainService.BirthdayInfo> result =
                domainService.calculateUpcoming(List.of(node), 400);

        assertEquals(1, result.size());
        long expected = ChronoUnit.DAYS.between(
                LocalDate.now(), birthDate.withYear(LocalDate.now().getYear() + 1));
        assertEquals(expected, result.get(0).getDaysUntil());
    }

    private FamilyNode buildNode(Long id, String name, LocalDate birthDate, boolean deceased) {
        FamilyNode node = new FamilyNode();
        node.setId(id);
        node.setName(name);
        node.setBirthDate(birthDate);
        if (deceased) {
            node.setDeathDate(LocalDate.of(2020, 1, 1));
        }
        return node;
    }
}
