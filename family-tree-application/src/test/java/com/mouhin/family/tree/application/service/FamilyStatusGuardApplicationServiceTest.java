package com.mouhin.family.tree.application.service;

import com.mouhin.family.tree.common.enums.FamilyStatusEnum;
import com.mouhin.family.tree.domain.entity.Family;
import com.mouhin.family.tree.domain.repository.FamilyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家族状态守卫应用服务单元测试：状态读取的失败开放边界。
 *
 * <p>治理标记只用于收紧权限，绝不能因数据缺失而误伤正常家族，因此未选家族、家族不存在、
 * 历史数据未落状态列三种情况一律按 NORMAL 处理。</p>
 *
 * @author Family-Tree
 * @date 2026-11-16
 */
@ExtendWith(MockitoExtension.class)
class FamilyStatusGuardApplicationServiceTest {

    private static final Long FAMILY_ID = 100L;

    @Mock
    private FamilyRepository familyRepository;

    private Family familyWithStatus(String status) {
        Family family = new Family();
        family.setId(FAMILY_ID);
        family.setName("蒙氏家族");
        family.setStatus(status);
        return family;
    }

    @Test
    void missingFamilyIdSkipsRepository() {
        FamilyStatusGuardApplicationService service = new FamilyStatusGuardApplicationService(familyRepository);

        assertEquals(FamilyStatusEnum.NORMAL, service.statusOf(null));
        verify(familyRepository, never()).findById(any());
    }

    @Test
    void unknownFamilyFallsBackToNormal() {
        FamilyStatusGuardApplicationService service = new FamilyStatusGuardApplicationService(familyRepository);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(null);

        assertEquals(FamilyStatusEnum.NORMAL, service.statusOf(FAMILY_ID));
    }

    @Test
    void legacyRowWithoutStatusColumnValueFallsBackToNormal() {
        FamilyStatusGuardApplicationService service = new FamilyStatusGuardApplicationService(familyRepository);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(familyWithStatus(null));

        assertEquals(FamilyStatusEnum.NORMAL, service.statusOf(FAMILY_ID));
    }

    @Test
    void governanceMarksAreReturnedAsIs() {
        FamilyStatusGuardApplicationService service = new FamilyStatusGuardApplicationService(familyRepository);
        when(familyRepository.findById(FAMILY_ID)).thenReturn(familyWithStatus("FROZEN"));
        assertEquals(FamilyStatusEnum.FROZEN, service.statusOf(FAMILY_ID));

        when(familyRepository.findById(FAMILY_ID)).thenReturn(familyWithStatus("ARCHIVED"));
        assertEquals(FamilyStatusEnum.ARCHIVED, service.statusOf(FAMILY_ID));
    }
}
