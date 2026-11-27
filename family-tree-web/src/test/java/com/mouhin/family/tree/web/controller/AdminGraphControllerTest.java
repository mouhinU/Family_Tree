package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.AdminGraphApplicationService;
import com.mouhin.family.tree.common.dto.AdminGraphDetailVO;
import com.mouhin.family.tree.common.dto.AdminGraphNetworkVO;
import com.mouhin.family.tree.common.dto.AdminGraphOverviewVO;
import com.mouhin.family.tree.common.result.Result;
import com.mouhin.family.tree.web.security.RequireSystemAdmin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台跨族图谱控制器单元测试
 *
 * @author Family-Tree
 * @date 2026-11-26
 */
@ExtendWith(MockitoExtension.class)
class AdminGraphControllerTest {

    @Mock
    private AdminGraphApplicationService adminGraphService;

    @InjectMocks
    private AdminGraphController controller;

    @Test
    void overview_returnsServiceResult() {
        AdminGraphOverviewVO data = new AdminGraphOverviewVO();
        when(adminGraphService.getOverview()).thenReturn(data);

        Result<AdminGraphOverviewVO> result = controller.overview();

        assertEquals(200, result.getCode());
        assertSame(data, result.getData());
        verify(adminGraphService).getOverview();
    }

    @Test
    void familyDetail_delegatesFamilyId() {
        AdminGraphDetailVO data = new AdminGraphDetailVO();
        when(adminGraphService.getFamilyDetail(9L)).thenReturn(data);

        Result<AdminGraphDetailVO> result = controller.familyDetail(9L);

        assertEquals(200, result.getCode());
        assertSame(data, result.getData());
        verify(adminGraphService).getFamilyDetail(9L);
    }

    @Test
    void familyNetwork_delegatesFamilyId() {
        AdminGraphNetworkVO data = new AdminGraphNetworkVO();
        when(adminGraphService.getFamilyNetwork(9L)).thenReturn(data);

        Result<AdminGraphNetworkVO> result = controller.familyNetwork(9L);

        assertEquals(200, result.getCode());
        assertSame(data, result.getData());
        verify(adminGraphService).getFamilyNetwork(9L);
    }

    @Test
    void endpointsRequireSystemAdmin() throws NoSuchMethodException {
        assertNotNull(AdminGraphController.class.getMethod("overview").getAnnotation(RequireSystemAdmin.class));
        assertNotNull(AdminGraphController.class.getMethod("familyDetail", Long.class)
                .getAnnotation(RequireSystemAdmin.class));
        assertNotNull(AdminGraphController.class.getMethod("familyNetwork", Long.class)
                .getAnnotation(RequireSystemAdmin.class));
    }
}
