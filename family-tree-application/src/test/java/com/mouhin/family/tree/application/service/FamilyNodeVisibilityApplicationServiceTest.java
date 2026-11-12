package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.RelationTypeEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyMember;
import com.mouhin.family.tree.domain.entity.FamilyRelation;
import com.mouhin.family.tree.domain.entity.FamilyUserNode;
import com.mouhin.family.tree.domain.repository.FamilyMemberRepository;
import com.mouhin.family.tree.domain.repository.FamilyRelationRepository;
import com.mouhin.family.tree.domain.repository.FamilyUserNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 族谱节点详情可见性应用服务单元测试。
 * 覆盖：管理员不受限、未绑定空集、血缘链+直系配偶、配偶原生分支不展开、过继父链、越权拒绝。
 *
 * @author Family-Tree
 * @date 2026-11-11
 */
@ExtendWith(MockitoExtension.class)
class FamilyNodeVisibilityApplicationServiceTest {

    private static final Long FAMILY_ID = 1000L;
    private static final Long USER_ID = 5000L;
    private static final Long SELF = 400L;

    @Mock
    private FamilyMemberRepository familyMemberRepository;

    @Mock
    private FamilyUserNodeRepository familyUserNodeRepository;

    @Mock
    private FamilyRelationRepository familyRelationRepository;

    @InjectMocks
    private FamilyNodeVisibilityApplicationService service;

    // ========== 管理员 ==========

    @Test
    void resolve_manager_returnsNullAndSkipsLookups() {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(member("OWNER"));

        Set<Long> result = service.resolveDetailVisibleNodeIds(FAMILY_ID, USER_ID);

        assertNull(result);
        verify(familyUserNodeRepository, never()).findByFamilyIdAndUserId(anyLong(), anyLong());
        verify(familyRelationRepository, never()).findByFamilyId(anyLong());
    }

    @Test
    void assertDetailVisible_manager_allowsAnyNode() {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(member("ADMIN"));

        service.assertDetailVisible(FAMILY_ID, USER_ID, 999999L);
    }

    // ========== 未绑定 ==========

    @Test
    void resolve_unbound_returnsEmptyAndSkipsRelations() {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(member("MEMBER"));
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);

        Set<Long> result = service.resolveDetailVisibleNodeIds(FAMILY_ID, USER_ID);

        assertTrue(result.isEmpty());
        verify(familyRelationRepository, never()).findByFamilyId(anyLong());
    }

    // ========== 血缘链 + 直系配偶 ==========

    @Test
    void resolve_member_coversAncestorsDescendantsAndDirectSpouses() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        Set<Long> result = service.resolveDetailVisibleNodeIds(FAMILY_ID, USER_ID);

        // 自身 + 祖先(曾祖/祖父母及配偶) + 后代(子/孙) + 各核心节点直系配偶
        assertTrue(result.containsAll(List.of(
                100L, 101L, 200L, 201L, 300L, 301L, SELF, 401L, 500L, 501L, 600L)));
        // 旁系（兄弟姐妹/侄）与配偶原生分支（继子）不可见
        assertFalse(result.contains(700L));
        assertFalse(result.contains(701L));
        assertFalse(result.contains(702L));
        assertFalse(result.contains(800L));
    }

    @Test
    void resolve_followsAdoptionAsParentLink() {
        stubMember("MEMBER");
        stubBinding(SELF);
        List<FamilyRelation> relations = new ArrayList<>();
        relations.add(rel(900L, SELF, RelationTypeEnum.ADOPTION.getCode()));
        relations.add(rel(900L, 901L, RelationTypeEnum.SPOUSE.getCode()));
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(relations);

        Set<Long> result = service.resolveDetailVisibleNodeIds(FAMILY_ID, USER_ID);

        assertTrue(result.containsAll(List.of(SELF, 900L, 901L)));
    }

    // ========== 越权拒绝 ==========

    @Test
    void assertDetailVisible_inScope_passes() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        service.assertDetailVisible(FAMILY_ID, USER_ID, 500L);
        service.assertDetailVisible(FAMILY_ID, USER_ID, SELF);
    }

    @Test
    void assertDetailVisible_outOfScope_throws() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertDetailVisible(FAMILY_ID, USER_ID, 700L));
        assertTrue(ex.getMessage().contains("无权查看"));
    }

    @Test
    void assertDetailVisible_nullNode_throws() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        assertThrows(BusinessException.class,
                () -> service.assertDetailVisible(FAMILY_ID, USER_ID, null));
    }

    // ========== 操作门：assertOperableNode ==========

    @Test
    void assertOperableNode_manager_allowsAnyNode() {
        stubMember("ADMIN");

        service.assertOperableNode(FAMILY_ID, USER_ID, 999999L);
    }

    @Test
    void assertOperableNode_inScope_passes() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        service.assertOperableNode(FAMILY_ID, USER_ID, 500L);
        service.assertOperableNode(FAMILY_ID, USER_ID, SELF);
    }

    @Test
    void assertOperableNode_outOfScope_throws() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertOperableNode(FAMILY_ID, USER_ID, 700L));
        assertTrue(ex.getMessage().contains("无权操作"));
    }

    @Test
    void assertOperableNode_unboundMember_throws() {
        stubMember("MEMBER");
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.assertOperableNode(FAMILY_ID, USER_ID, SELF));
    }

    // ========== 操作门：assertOperableNodes（关系两端） ==========

    @Test
    void assertOperableNodes_allInScope_passes() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        service.assertOperableNodes(FAMILY_ID, USER_ID, SELF, 500L, 501L);
    }

    @Test
    void assertOperableNodes_oneOutOfScope_throws() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertOperableNodes(FAMILY_ID, USER_ID, SELF, 700L));
        assertTrue(ex.getMessage().contains("无权操作"));
    }

    @Test
    void assertOperableNodes_nullElement_throws() {
        stubMember("MEMBER");
        stubBinding(SELF);
        when(familyRelationRepository.findByFamilyId(FAMILY_ID)).thenReturn(lineageRelations());

        assertThrows(BusinessException.class,
                () -> service.assertOperableNodes(FAMILY_ID, USER_ID, SELF, null));
    }

    // ========== 整族级操作门：assertManager ==========

    @Test
    void assertManager_ownerAndAdmin_pass() {
        stubMember("OWNER");
        service.assertManager(FAMILY_ID, USER_ID);
    }

    @Test
    void assertManager_member_throws() {
        stubMember("MEMBER");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assertManager(FAMILY_ID, USER_ID));
        assertTrue(ex.getMessage().contains("仅管理员"));
    }

    // ========== 测试夹具 ==========

    private void stubMember(String role) {
        when(familyMemberRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(member(role));
    }

    private void stubBinding(Long nodeId) {
        FamilyUserNode binding = new FamilyUserNode();
        binding.setFamilyId(FAMILY_ID);
        binding.setUserId(USER_ID);
        binding.setNodeId(nodeId);
        when(familyUserNodeRepository.findByFamilyIdAndUserId(FAMILY_ID, USER_ID)).thenReturn(binding);
    }

    private FamilyMember member(String role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(FAMILY_ID);
        member.setUserId(USER_ID);
        member.setRole(role);
        return member;
    }

    private FamilyRelation rel(long from, long to, int type) {
        FamilyRelation relation = new FamilyRelation();
        relation.setFamilyId(FAMILY_ID);
        relation.setFromNodeId(from);
        relation.setToNodeId(to);
        relation.setRelationType(type);
        return relation;
    }

    private FamilyRelation pc(long parent, long child) {
        return rel(parent, child, RelationTypeEnum.PARENT_CHILD.getCode());
    }

    private FamilyRelation spouse(long a, long b) {
        return rel(a, b, RelationTypeEnum.SPOUSE.getCode());
    }

    /**
     * 三代以上血缘 + 旁系 + 配偶原生分支的完整夹具：
     * GP(100)+GPS(101) → R(200)+RS(201) → Mid(300)+MidS(301) → Self(400)+SelfS(401)
     * → Kid(500)+KidS(501) → Grand(600)；旁系 Sib(700)+SibSp(701)→Nephew(702)；
     * SelfS 前房子女 Step(800) 用于验证配偶分支不被展开。
     */
    private List<FamilyRelation> lineageRelations() {
        List<FamilyRelation> list = new ArrayList<>();
        list.add(pc(100L, 200L));
        list.add(pc(101L, 200L));
        list.add(pc(100L, 700L));
        list.add(pc(101L, 700L));
        list.add(pc(200L, 300L));
        list.add(pc(201L, 300L));
        list.add(pc(700L, 702L));
        list.add(pc(701L, 702L));
        list.add(pc(300L, SELF));
        list.add(pc(301L, SELF));
        list.add(pc(SELF, 500L));
        list.add(pc(401L, 500L));
        list.add(pc(401L, 800L));
        list.add(pc(500L, 600L));
        list.add(pc(501L, 600L));
        list.add(spouse(100L, 101L));
        list.add(spouse(200L, 201L));
        list.add(spouse(700L, 701L));
        list.add(spouse(300L, 301L));
        list.add(spouse(SELF, 401L));
        list.add(spouse(500L, 501L));
        return list;
    }
}
