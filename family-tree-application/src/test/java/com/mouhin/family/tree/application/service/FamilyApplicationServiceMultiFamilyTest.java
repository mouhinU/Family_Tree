package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.dto.FamilyJoinDTO;
import com.mouhin.family.tree.common.enums.FamilyMemberRoleEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.FamilyGenerationRepository;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import com.mouhin.family.tree.domain.repository.FamilyOfferingRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一个账号归属多个家族的单元测试。
 * 覆盖：家族数上限由平台配置判定、超限与重复加入时不写库、散落数据仅随首个家族迁移、
 * 新家族成为当前家族，以及当前家族读取优先 account.current_family_id。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class FamilyApplicationServiceMultiFamilyTest {

    private static final Long USER_ID = 7L;

    private static final Long NEW_FAMILY_ID = 100L;

    private static final Long OLD_FAMILY_ID = 1L;

    private static final Long SECOND_FAMILY_ID = 2L;

    private static final String OPERATOR_NAME = "mengbin";

    private static final String OPERATOR_IP = "127.0.0.1";

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
    private UserRepository userRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PlatformSettingApplicationService platformSettingService;

    @Mock
    private FamilyNodeApplicationService familyNodeService;

    @Mock
    private FamilyUserNodeApplicationService familyUserNodeService;

    @InjectMocks
    private FamilyApplicationService familyApplicationService;

    @Test
    void createFamily_allowsSecondFamilyWithinQuota() {
        givenCurrentUser(null);
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()));
        stubFamilyIdGenerated();

        Long familyId = familyApplicationService.createFamily(USER_ID, familyDTO(), OPERATOR_NAME, OPERATOR_IP);

        assertEquals(NEW_FAMILY_ID, familyId);
        verify(platformSettingService).assertUserFamilyQuotaAvailable(1);
        ArgumentCaptor<FamilyMember> memberCaptor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberRepository).save(memberCaptor.capture());
        assertEquals(NEW_FAMILY_ID, memberCaptor.getValue().getFamilyId());
        assertEquals(FamilyMemberRoleEnum.OWNER.getCode(), memberCaptor.getValue().getRole());
    }

    @Test
    void createFamily_rejectsWhenUserFamilyQuotaExceeded() {
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()),
                member(SECOND_FAMILY_ID, FamilyMemberRoleEnum.MEMBER.getCode()));
        doThrow(new BusinessException("账号可归属的家族数已达上限（2个），请联系平台管理员调整"))
                .when(platformSettingService).assertUserFamilyQuotaAvailable(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> familyApplicationService.createFamily(USER_ID, familyDTO(), OPERATOR_NAME, OPERATOR_IP));

        assertEquals("账号可归属的家族数已达上限（2个），请联系平台管理员调整", ex.getMessage());
        verify(familyRepository, never()).save(any());
        verify(familyMemberRepository, never()).save(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    void createFamily_marksNewFamilyAsCurrent() {
        givenCurrentUser(null);
        givenMemberships();
        stubFamilyIdGenerated();

        familyApplicationService.createFamily(USER_ID, familyDTO(), OPERATOR_NAME, OPERATOR_IP);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).update(captor.capture());
        assertEquals(NEW_FAMILY_ID, captor.getValue().getCurrentFamilyId());
    }

    @Test
    void createFamily_migratesScatteredUserDataForFirstFamilyOnly() {
        givenCurrentUser(null);
        givenMemberships();
        stubFamilyIdGenerated();

        familyApplicationService.createFamily(USER_ID, familyDTO(), OPERATOR_NAME, OPERATOR_IP);

        verify(familyNodeRepository).updateFamilyIdByUserId(USER_ID, NEW_FAMILY_ID);
        verify(familyRelationRepository).updateFamilyIdByUserId(USER_ID, NEW_FAMILY_ID);
        verify(familyGenerationRepository).updateFamilyIdByUserId(USER_ID, NEW_FAMILY_ID);
        verify(familyOfferingRepository).updateFamilyIdByUserId(USER_ID, NEW_FAMILY_ID);
    }

    @Test
    void createFamily_secondFamilyKeepsExistingFamilyDataUntouched() {
        givenCurrentUser(OLD_FAMILY_ID);
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()));
        stubFamilyIdGenerated();

        familyApplicationService.createFamily(USER_ID, familyDTO(), OPERATOR_NAME, OPERATOR_IP);

        verify(familyNodeRepository, never()).updateFamilyIdByUserId(anyLong(), anyLong());
        verify(familyRelationRepository, never()).updateFamilyIdByUserId(anyLong(), anyLong());
        verify(familyGenerationRepository, never()).updateFamilyIdByUserId(anyLong(), anyLong());
        verify(familyOfferingRepository, never()).updateFamilyIdByUserId(anyLong(), anyLong());
    }

    @Test
    void joinFamily_rejectsWhenAlreadyInTargetFamily() {
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.MEMBER.getCode()));
        when(familyRepository.findByInviteCode(anyString())).thenReturn(family(OLD_FAMILY_ID, "旧家族"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> familyApplicationService.joinFamily(USER_ID, joinDTO()));

        assertEquals("您已在该家族中", ex.getMessage());
        verify(familyMemberRepository, never()).save(any());
        verify(userRepository, never()).update(any());
    }

    @Test
    void getCurrentFamily_prefersFamilyMarkedAsCurrent() {
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.MEMBER.getCode()),
                member(NEW_FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()));
        givenCurrentUser(NEW_FAMILY_ID);
        when(familyRepository.findById(NEW_FAMILY_ID)).thenReturn(family(NEW_FAMILY_ID, "新家族"));

        FamilyDTO dto = familyApplicationService.getCurrentFamily(USER_ID);

        assertEquals(NEW_FAMILY_ID, dto.getId());
        assertEquals(FamilyMemberRoleEnum.OWNER.getCode(), dto.getCurrentRole());
    }

    @Test
    void getCurrentFamily_fallsBackToFirstMembershipWhenCurrentFamilyIdStale() {
        givenMemberships(member(OLD_FAMILY_ID, FamilyMemberRoleEnum.MEMBER.getCode()),
                member(NEW_FAMILY_ID, FamilyMemberRoleEnum.OWNER.getCode()));
        givenCurrentUser(999L);
        when(familyRepository.findById(OLD_FAMILY_ID)).thenReturn(family(OLD_FAMILY_ID, "旧家族"));

        FamilyDTO dto = familyApplicationService.getCurrentFamily(USER_ID);

        assertEquals(OLD_FAMILY_ID, dto.getId());
        assertEquals(FamilyMemberRoleEnum.MEMBER.getCode(), dto.getCurrentRole());
    }

    @Test
    void getCurrentFamily_returnsNullWithoutMembership() {
        givenMemberships();

        assertNull(familyApplicationService.getCurrentFamily(USER_ID));
    }

    private void givenMemberships(FamilyMember... records) {
        when(familyMemberRepository.findByUserId(USER_ID)).thenReturn(List.of(records));
    }

    private void givenCurrentUser(Long currentFamilyId) {
        User user = new User();
        user.setId(USER_ID);
        user.setCurrentFamilyId(currentFamilyId);
        when(userRepository.findById(USER_ID)).thenReturn(user);
    }

    /**
     * 让仓储保存家族时回填主键，模拟数据库自增
     */
    private void stubFamilyIdGenerated() {
        doAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            family.setId(NEW_FAMILY_ID);
            return null;
        }).when(familyRepository).save(any(Family.class));
    }

    private FamilyMember member(Long familyId, String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(familyId);
        member.setUserId(USER_ID);
        member.setRole(role);
        return member;
    }

    private Family family(Long familyId, String name) {
        Family family = new Family();
        family.setId(familyId);
        family.setName(name);
        return family;
    }

    private FamilyCreateDTO familyDTO() {
        FamilyCreateDTO dto = new FamilyCreateDTO();
        dto.setName("mengshi");
        return dto;
    }

    private FamilyJoinDTO joinDTO() {
        FamilyJoinDTO dto = new FamilyJoinDTO();
        dto.setInviteCode("AB3CD4EF");
        return dto;
    }
}
