package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.dto.AnniversaryDTO;
import com.mouhin.family.tree.common.dto.AnniversaryVO;
import com.mouhin.family.tree.common.enums.AnniversaryCategoryEnum;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.domain.entity.FamilyAnniversary;
import com.mouhin.family.tree.domain.entity.FamilyNode;
import com.mouhin.family.tree.domain.repository.AnniversaryRepository;
import com.mouhin.family.tree.domain.repository.FamilyNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 家族纪念日应用服务单元测试。
 * 覆盖：新增纪念日（正常/默认分类/非法分类/日期格式/节点不存在）、
 * 列表查询（空/节点名称解析/按临近天数排序/周数计算）、
 * 更新纪念日（正常/仅创建者）、删除纪念日（正常/仅创建者/不存在）。
 *
 * @author Family-Tree
 * @date 2026-08-31
 */
@ExtendWith(MockitoExtension.class)
class AnniversaryApplicationServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long FAMILY_ID = 100L;
    private static final Long NODE_ID = 30L;
    private static final Long ANNIVERSARY_ID = 40L;

    @Mock
    private AnniversaryRepository anniversaryRepository;

    @Mock
    private FamilyNodeRepository familyNodeRepository;

    @InjectMocks
    private AnniversaryApplicationService anniversaryApplicationService;

    // ========== 新增纪念日 ==========

    @Test
    void createAnniversary_success() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode());

        AnniversaryDTO dto = buildDTO("wedding", "2020-05-20");
        dto.setTitle("  结婚纪念日  ");

        when(anniversaryRepository.save(any(FamilyAnniversary.class))).thenAnswer(invocation -> {
            FamilyAnniversary anniversary = invocation.getArgument(0);
            anniversary.setId(ANNIVERSARY_ID);
            return anniversary;
        });

        Long id = anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto);

        assertEquals(ANNIVERSARY_ID, id);
        ArgumentCaptor<FamilyAnniversary> captor = ArgumentCaptor.forClass(FamilyAnniversary.class);
        verify(anniversaryRepository).save(captor.capture());
        FamilyAnniversary saved = captor.getValue();
        assertEquals(FAMILY_ID, saved.getFamilyId());
        assertEquals(NODE_ID, saved.getNodeId());
        assertEquals("结婚纪念日", saved.getTitle());
        assertEquals("wedding", saved.getCategory());
        assertEquals(LocalDate.of(2020, 5, 20), saved.getAnniversaryDate());
    }

    @Test
    void createAnniversary_blankCategory_defaultsToOther() {
        AnniversaryDTO dto = buildDTO(null, "2020-05-20");
        dto.setNodeId(null);

        anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto);

        ArgumentCaptor<FamilyAnniversary> captor = ArgumentCaptor.forClass(FamilyAnniversary.class);
        verify(anniversaryRepository).save(captor.capture());
        assertEquals(AnniversaryCategoryEnum.OTHER.getCode(), captor.getValue().getCategory());
        assertNull(captor.getValue().getNodeId());
    }

    @Test
    void createAnniversary_invalidCategory_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode());

        AnniversaryDTO dto = buildDTO("invalid_category", "2020-05-20");

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto));
        verify(anniversaryRepository, never()).save(any(FamilyAnniversary.class));
    }

    @Test
    void createAnniversary_invalidDate_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode());

        AnniversaryDTO dto = buildDTO("wedding", "2020/05/20");

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto));
        verify(anniversaryRepository, never()).save(any(FamilyAnniversary.class));
    }

    @Test
    void createAnniversary_blankDate_throws() {
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(buildNode());

        AnniversaryDTO dto = buildDTO("wedding", "  ");

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto));
        verify(anniversaryRepository, never()).save(any(FamilyAnniversary.class));
    }

    @Test
    void createAnniversary_nodeOtherFamily_throws() {
        FamilyNode node = buildNode();
        node.setFamilyId(999L);
        when(familyNodeRepository.findById(NODE_ID)).thenReturn(node);

        AnniversaryDTO dto = buildDTO("wedding", "2020-05-20");

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.createAnniversary(FAMILY_ID, USER_ID, dto));
        verify(anniversaryRepository, never()).save(any(FamilyAnniversary.class));
    }

    // ========== 列表查询 ==========

    @Test
    void listAnniversaries_empty() {
        when(anniversaryRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of());

        assertTrue(anniversaryApplicationService.listAnniversaries(FAMILY_ID, USER_ID).isEmpty());
        verify(familyNodeRepository, never()).findByIds(any());
    }

    @Test
    void listAnniversaries_sortedByDaysUntil_withNodeName() {
        LocalDate nearDate = LocalDate.now().plusDays(3).minusYears(5);
        LocalDate farDate = LocalDate.now().plusDays(6).minusYears(10);
        FamilyAnniversary near = buildAnniversary(1L, USER_ID, nearDate);
        FamilyAnniversary far = buildAnniversary(2L, OTHER_USER_ID, farDate);
        when(anniversaryRepository.findByFamilyId(FAMILY_ID)).thenReturn(List.of(far, near));

        FamilyNode node = buildNode();
        when(familyNodeRepository.findByIds(List.of(NODE_ID))).thenReturn(List.of(node));

        List<AnniversaryVO> result =
                anniversaryApplicationService.listAnniversaries(FAMILY_ID, USER_ID);

        assertEquals(2, result.size());
        // 按距下次纪念日天数升序排列
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
        assertTrue(result.get(0).getDaysUntil() <= result.get(1).getDaysUntil());
        assertEquals(5, result.get(0).getYears());
        assertEquals(10, result.get(1).getYears());
        assertEquals("张三", result.get(0).getNodeName());
        assertTrue(result.get(0).getOwn());
        assertFalse(result.get(1).getOwn());
        assertEquals(AnniversaryCategoryEnum.WEDDING.getDescription(), result.get(0).getCategoryDesc());
    }

    // ========== 更新纪念日 ==========

    @Test
    void updateAnniversary_success() {
        FamilyAnniversary anniversary = buildAnniversary(ANNIVERSARY_ID, USER_ID,
                LocalDate.of(2020, 5, 20));
        when(anniversaryRepository.findById(ANNIVERSARY_ID)).thenReturn(anniversary);

        AnniversaryDTO dto = buildDTO(null, null);
        dto.setNodeId(null);
        dto.setTitle("新标题");

        anniversaryApplicationService.updateAnniversary(FAMILY_ID, ANNIVERSARY_ID, USER_ID, dto);

        ArgumentCaptor<FamilyAnniversary> captor = ArgumentCaptor.forClass(FamilyAnniversary.class);
        verify(anniversaryRepository).update(captor.capture());
        assertEquals("新标题", captor.getValue().getTitle());
        // 未传的日期保持原值
        assertEquals(LocalDate.of(2020, 5, 20), captor.getValue().getAnniversaryDate());
    }

    @Test
    void updateAnniversary_notOwner_throws() {
        FamilyAnniversary anniversary = buildAnniversary(ANNIVERSARY_ID, OTHER_USER_ID,
                LocalDate.of(2020, 5, 20));
        when(anniversaryRepository.findById(ANNIVERSARY_ID)).thenReturn(anniversary);

        AnniversaryDTO dto = buildDTO(null, null);

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.updateAnniversary(FAMILY_ID, ANNIVERSARY_ID, USER_ID, dto));
        verify(anniversaryRepository, never()).update(any(FamilyAnniversary.class));
    }

    // ========== 删除纪念日 ==========

    @Test
    void deleteAnniversary_success() {
        FamilyAnniversary anniversary = buildAnniversary(ANNIVERSARY_ID, USER_ID,
                LocalDate.of(2020, 5, 20));
        when(anniversaryRepository.findById(ANNIVERSARY_ID)).thenReturn(anniversary);

        anniversaryApplicationService.deleteAnniversary(FAMILY_ID, ANNIVERSARY_ID, USER_ID);

        verify(anniversaryRepository).removeById(ANNIVERSARY_ID);
    }

    @Test
    void deleteAnniversary_notOwner_throws() {
        FamilyAnniversary anniversary = buildAnniversary(ANNIVERSARY_ID, OTHER_USER_ID,
                LocalDate.of(2020, 5, 20));
        when(anniversaryRepository.findById(ANNIVERSARY_ID)).thenReturn(anniversary);

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.deleteAnniversary(FAMILY_ID, ANNIVERSARY_ID, USER_ID));
        verify(anniversaryRepository, never()).removeById(any());
    }

    @Test
    void deleteAnniversary_notFound_throws() {
        when(anniversaryRepository.findById(999L)).thenReturn(null);

        assertThrows(BusinessException.class, () ->
                anniversaryApplicationService.deleteAnniversary(FAMILY_ID, 999L, USER_ID));
        verify(anniversaryRepository, never()).removeById(any());
    }

    // ========== 辅助方法 ==========

    private AnniversaryDTO buildDTO(String category, String anniversaryDate) {
        AnniversaryDTO dto = new AnniversaryDTO();
        dto.setNodeId(NODE_ID);
        dto.setTitle("结婚纪念日");
        dto.setCategory(category);
        dto.setAnniversaryDate(anniversaryDate);
        return dto;
    }

    private FamilyNode buildNode() {
        FamilyNode node = new FamilyNode();
        node.setId(NODE_ID);
        node.setFamilyId(FAMILY_ID);
        node.setName("张三");
        return node;
    }

    private FamilyAnniversary buildAnniversary(Long id, Long userId, LocalDate date) {
        FamilyAnniversary anniversary = new FamilyAnniversary();
        anniversary.setId(id);
        anniversary.setFamilyId(FAMILY_ID);
        anniversary.setNodeId(NODE_ID);
        anniversary.setUserId(userId);
        anniversary.setTitle("纪念日" + id);
        anniversary.setCategory(AnniversaryCategoryEnum.WEDDING.getCode());
        anniversary.setAnniversaryDate(date);
        anniversary.setCreateTime(LocalDateTime.now());
        anniversary.setUpdateTime(LocalDateTime.now());
        return anniversary;
    }
}
