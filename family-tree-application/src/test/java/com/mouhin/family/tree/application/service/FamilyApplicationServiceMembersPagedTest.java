package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyMemberDTO;
import com.mouhin.family.tree.common.dto.PageResult;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.User;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家族成员分页查询单元测试。
 * 覆盖：总数为 0 短路、offset 分页计算、角色与昵称映射、缺用户时昵称为空。
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
@ExtendWith(MockitoExtension.class)
class FamilyApplicationServiceMembersPagedTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FamilyApplicationService familyApplicationService;

    @Test
    void listMembersPaged_zeroTotal_returnsEmptyWithoutPagedQuery() {
        when(familyMemberRepository.countByFamilyId(FAMILY_ID)).thenReturn(0L);

        PageResult<FamilyMemberDTO> result = familyApplicationService.listMembersPaged(FAMILY_ID, 1, 20);

        assertEquals(0L, result.getTotal());
        assertEquals(0, result.getTotalPages());
        assertEquals(0, result.getRecords().size());
        verify(familyMemberRepository, never()).findByFamilyIdPaged(any(), anyInt(), anyInt());
    }

    @Test
    void listMembersPaged_secondPage_usesOffset() {
        when(familyMemberRepository.countByFamilyId(FAMILY_ID)).thenReturn(102L);
        when(familyMemberRepository.findByFamilyIdPaged(FAMILY_ID, 20, 20)).thenReturn(List.of());

        familyApplicationService.listMembersPaged(FAMILY_ID, 2, 20);

        verify(familyMemberRepository).findByFamilyIdPaged(eq(FAMILY_ID), eq(20), eq(20));
    }

    @Test
    void listMembersPaged_mapsRoleAndNickname() {
        when(familyMemberRepository.countByFamilyId(FAMILY_ID)).thenReturn(102L);
        when(familyMemberRepository.findByFamilyIdPaged(FAMILY_ID, 0, 20)).thenReturn(List.of(
                member(1L, "OWNER"), member(2L, "MEMBER"), member(3L, "MEMBER")));
        when(userRepository.findByIds(any())).thenReturn(List.of(user(1L, "族长"), user(2L, "成员甲")));

        PageResult<FamilyMemberDTO> result = familyApplicationService.listMembersPaged(FAMILY_ID, 1, 20);

        assertEquals(6, result.getTotalPages());
        assertEquals(102L, result.getTotal());
        List<FamilyMemberDTO> records = result.getRecords();
        assertEquals(3, records.size());
        assertEquals("族长", records.get(0).getNickname());
        assertEquals("OWNER", records.get(0).getRole());
        assertEquals("成员甲", records.get(1).getNickname());
        assertNull(records.get(2).getNickname(), "无对应用户时昵称应为空");
    }

    private FamilyMember member(Long userId, String role) {
        FamilyMember m = new FamilyMember();
        m.setFamilyId(FAMILY_ID);
        m.setUserId(userId);
        m.setRole(role);
        return m;
    }

    private User user(Long id, String nickname) {
        User u = new User();
        u.setId(id);
        u.setNickname(nickname);
        return u;
    }
}
