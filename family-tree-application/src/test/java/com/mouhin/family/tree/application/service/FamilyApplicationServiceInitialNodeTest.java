package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.FamilyCreateDTO;
import com.mouhin.family.tree.common.dto.NodeCreateDTO;
import com.mouhin.family.tree.domain.entity.Family;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 创建家族时初始化首个族谱节点的单元测试。
 * 覆盖：填写姓名时建节点并绑定创建者、姓名为空时跳过、姓名首尾空格规整。
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class FamilyApplicationServiceInitialNodeTest {

    private static final Long USER_ID = 7L;

    private static final Long NEW_FAMILY_ID = 100L;

    private static final Long NEW_NODE_ID = 555L;

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

    /**
     * 建族要把新家族标记为该账号的当前家族，因此需要账号行存在
     */
    private void stubCurrentUser() {
        User user = new User();
        user.setId(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(user);
    }

    private FamilyCreateDTO buildDTO(String initialNodeName) {
        FamilyCreateDTO dto = new FamilyCreateDTO();
        dto.setName("mengshi");
        dto.setInitialNodeName(initialNodeName);
        return dto;
    }

    @Test
    void createFamily_withInitialNodeName_createsNodeAndBindsCreator() {
        stubFamilyIdGenerated();
        stubCurrentUser();
        when(familyNodeService.createNode(anyLong(), anyLong(), any(), any(), any(NodeCreateDTO.class)))
                .thenReturn(NEW_NODE_ID);

        FamilyCreateDTO dto = buildDTO("mengbin");
        dto.setInitialNodeGender(1);
        dto.setInitialNodeBirthDate("1990-03-05");
        dto.setInitialNodeBirthCalendar(2);

        Long familyId = familyApplicationService.createFamily(USER_ID, dto, OPERATOR_NAME, OPERATOR_IP);

        assertEquals(NEW_FAMILY_ID, familyId);
        ArgumentCaptor<NodeCreateDTO> captor = ArgumentCaptor.forClass(NodeCreateDTO.class);
        verify(familyNodeService).createNode(eq(NEW_FAMILY_ID), eq(USER_ID), eq(OPERATOR_NAME),
                eq(OPERATOR_IP), captor.capture());
        NodeCreateDTO nodeDTO = captor.getValue();
        assertEquals("mengbin", nodeDTO.getName());
        assertEquals(Integer.valueOf(1), nodeDTO.getGender());
        assertEquals("1990-03-05", nodeDTO.getBirthDate());
        assertEquals(Integer.valueOf(2), nodeDTO.getBirthCalendar());
        verify(familyUserNodeService).bindMyNode(NEW_FAMILY_ID, USER_ID, NEW_NODE_ID);
    }

    @Test
    void createFamily_blankInitialNodeName_skipsNodeCreation() {
        stubFamilyIdGenerated();
        stubCurrentUser();

        Long familyId = familyApplicationService.createFamily(USER_ID, buildDTO("   "),
                OPERATOR_NAME, OPERATOR_IP);

        assertEquals(NEW_FAMILY_ID, familyId);
        verifyNoInteractions(familyNodeService);
        verifyNoInteractions(familyUserNodeService);
    }

    @Test
    void createFamily_trimsInitialNodeName() {
        stubFamilyIdGenerated();
        stubCurrentUser();
        when(familyNodeService.createNode(anyLong(), anyLong(), any(), any(), any(NodeCreateDTO.class)))
                .thenReturn(NEW_NODE_ID);

        familyApplicationService.createFamily(USER_ID, buildDTO("  mengbin  "), OPERATOR_NAME, OPERATOR_IP);

        ArgumentCaptor<NodeCreateDTO> captor = ArgumentCaptor.forClass(NodeCreateDTO.class);
        verify(familyNodeService).createNode(anyLong(), anyLong(), any(), any(), captor.capture());
        assertEquals("mengbin", captor.getValue().getName());
        verify(familyUserNodeService).bindMyNode(NEW_FAMILY_ID, USER_ID, NEW_NODE_ID);
    }

    @Test
    void createFamily_nodeCreationFails_doesNotBind() {
        stubFamilyIdGenerated();
        stubCurrentUser();
        when(familyNodeService.createNode(anyLong(), anyLong(), any(), any(), any(NodeCreateDTO.class)))
                .thenThrow(new IllegalStateException("quota exceeded"));

        assertThrows(IllegalStateException.class, () -> familyApplicationService.createFamily(
                USER_ID, buildDTO("mengbin"), OPERATOR_NAME, OPERATOR_IP));

        verify(familyUserNodeService, never()).bindMyNode(any(), any(), any());
    }
}
