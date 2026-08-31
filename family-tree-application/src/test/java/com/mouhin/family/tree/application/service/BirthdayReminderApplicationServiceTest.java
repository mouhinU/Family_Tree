package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.BirthdayReminderVO;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.service.BirthdayReminderDomainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 生日提醒应用服务单元测试。
 * 覆盖：未来生日查询（正常映射/空结果）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class BirthdayReminderApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private BirthdayReminderDomainService birthdayReminderDomainService;

    @InjectMocks
    private BirthdayReminderApplicationService birthdayReminderApplicationService;

    @Test
    void getUpcoming_success() {
        FamilyNode node = new FamilyNode();
        node.setId(1L);
        node.setFamilyId(FAMILY_ID);
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node));

        LocalDate birthDate = LocalDate.of(1990, 5, 20);
        BirthdayReminderDomainService.BirthdayInfo info =
                new BirthdayReminderDomainService.BirthdayInfo(1L, "张三", birthDate, 36, 3);
        when(birthdayReminderDomainService.calculateUpcoming(List.of(node), 7))
                .thenReturn(List.of(info));

        List<BirthdayReminderVO> result = birthdayReminderApplicationService.getUpcoming(FAMILY_ID, 7);

        assertEquals(1, result.size());
        BirthdayReminderVO vo = result.get(0);
        assertEquals(1L, vo.getNodeId());
        assertEquals("张三", vo.getName());
        assertEquals("1990-05-20", vo.getBirthDate());
        assertEquals(36, vo.getAge());
        assertEquals(3, vo.getDaysUntil());
    }

    @Test
    void getUpcoming_empty() {
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());
        when(birthdayReminderDomainService.calculateUpcoming(List.of(), 7)).thenReturn(List.of());

        assertTrue(birthdayReminderApplicationService.getUpcoming(FAMILY_ID, 7).isEmpty());
        verify(birthdayReminderDomainService).calculateUpcoming(List.of(), 7);
    }

    @Test
    void getUpcoming_nullBirthDate_mapsToNull() {
        FamilyNode node = new FamilyNode();
        node.setId(1L);
        when(familyNodeRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(node));

        BirthdayReminderDomainService.BirthdayInfo info =
                new BirthdayReminderDomainService.BirthdayInfo(1L, "张三", null, 30, 0);
        when(birthdayReminderDomainService.calculateUpcoming(List.of(node), 7))
                .thenReturn(List.of(info));

        List<BirthdayReminderVO> result = birthdayReminderApplicationService.getUpcoming(FAMILY_ID, 7);

        assertNull(result.get(0).getBirthDate());
    }
}
