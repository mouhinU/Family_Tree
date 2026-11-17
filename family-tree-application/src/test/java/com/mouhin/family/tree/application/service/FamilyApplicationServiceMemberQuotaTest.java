package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.event.MemberJoinedEvent;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyOfferingRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邀请码加入家族的成员数上限单元测试。
 * 覆盖：上限判定改由平台配置 MAX_FAMILY_MEMBERS 承担、超限拒绝时不写成员表、
 * 未超限时完成加入与数据迁移。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class FamilyApplicationServiceMemberQuotaTest {

    private static final Long FAMILY_ID = 100L;

    private static final Long USER_ID = 7L;

    private static final String INVITE_CODE = "AB3CD4EF";

    @Mock
    private FamilyRepository familyRepository;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @Mock
    private FamilyGenerationRepository familyGenerationRepository;

    @Mock
    private FamilyOfferingRepository familyOfferingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformSettingApplicationService platformSettingService;

    @InjectMocks
    private FamilyApplicationService familyApplicationService;

    @Test
    void joinFamily_checksMemberQuotaFromPlatformSetting() {
        givenInvitedFamily();

        familyApplicationService.joinFamily(USER_ID, joinDTO());

        verify(platformSettingService).assertMemberQuotaAvailable(FAMILY_ID);
        verify(familyMemberRepository).save(any());
        verify(eventPublisher).publishEvent(any(MemberJoinedEvent.class));
    }

    @Test
    void joinFamily_rejectsWhenMemberQuotaExceeded() {
        givenInvitedFamily();
        doThrow(new BusinessException("家族成员数已达上限（3人），请联系平台管理员调整"))
                .when(platformSettingService).assertMemberQuotaAvailable(FAMILY_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> familyApplicationService.joinFamily(USER_ID, joinDTO()));

        assertEquals("家族成员数已达上限（3人），请联系平台管理员调整", ex.getMessage());
        verify(familyMemberRepository, never()).save(any());
        verify(familyNodeRepository, never()).updateFamilyIdByUserId(anyLong(), anyLong());
        verify(eventPublisher, never()).publishEvent(any(MemberJoinedEvent.class));
    }

    @Test
    void joinFamily_migratesUserDataAfterQuotaPasses() {
        givenInvitedFamily();

        familyApplicationService.joinFamily(USER_ID, joinDTO());

        verify(familyNodeRepository).updateFamilyIdByUserId(USER_ID, FAMILY_ID);
        verify(familyRelationRepository).updateFamilyIdByUserId(USER_ID, FAMILY_ID);
        verify(familyGenerationRepository).updateFamilyIdByUserId(USER_ID, FAMILY_ID);
        verify(familyOfferingRepository).updateFamilyIdByUserId(USER_ID, FAMILY_ID);
        ArgumentCaptor<FamilyMember> captor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberRepository).save(captor.capture());
        assertEquals(FAMILY_ID, captor.getValue().getFamilyId());
        assertEquals(USER_ID, captor.getValue().getUserId());
    }

    private void givenInvitedFamily() {
        when(familyMemberRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(familyRepository.findByInviteCode(anyString())).thenReturn(family());
    }

    private FamilyJoinDTO joinDTO() {
        FamilyJoinDTO dto = new FamilyJoinDTO();
        dto.setInviteCode(INVITE_CODE);
        return dto;
    }

    private Family family() {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("上限家族");
        family.setInviteCode(INVITE_CODE);
        return family;
    }
}
