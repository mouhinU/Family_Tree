package com.mouhin.family.tree.web.controller;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ReaderException;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.common.constant.FamilyTreeConsts;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邀请控制器单元测试：验证邀请码一律取库内真实码、外部可访问链接的反代还原规则，
 * 以及二维码内编码的正是同一个邀请链接。
 *
 * @author Family-Tree
 * @date 2026-11-17
 */
@ExtendWith(MockitoExtension.class)
class InviteControllerTest {

    private static final long FAMILY_ID = 7L;
    private static final long USER_ID = 100L;
    private static final String INVITE_CODE = "665AYXS6";

    @Mock
    private FamilyApplicationService familyApplicationService;

    private InviteController inviteController;

    @BeforeEach
    void setUp() {
        inviteController = new InviteController(familyApplicationService);
    }

    // ========== 邀请链接 ==========

    @Test
    void getInviteLink_usesStoredCodeOfSessionFamily() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));

        Map<String, Object> data = inviteController.getInviteLink(session(), directRequest()).getData();

        assertEquals(INVITE_CODE, data.get("inviteCode"));
        assertEquals(Long.valueOf(FAMILY_ID), data.get("familyId"));
        verify(familyApplicationService).getFamilyById(FAMILY_ID);
    }

    @Test
    void getInviteLink_buildsAbsoluteUrlFromHostHeader() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));
        MockHttpServletRequest request = directRequest();
        request.addHeader("Host", "tree.example.com:8090");

        String url = (String) inviteController.getInviteLink(session(), request).getData().get("inviteUrl");

        assertEquals("http://tree.example.com:8090/login.html?invite=" + INVITE_CODE, url);
    }

    @Test
    void getInviteLink_prefersForwardedProtoBehindProxy() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("family-tree-app");
        request.setServerPort(8090);
        request.addHeader("Host", "tree.example.com");
        request.addHeader("X-Forwarded-Proto", "https");

        String url = (String) inviteController.getInviteLink(session(), request).getData().get("inviteUrl");

        assertEquals("https://tree.example.com/login.html?invite=" + INVITE_CODE, url);
    }

    @Test
    void getInviteLink_fallsBackToRelativePathWhenHostHeaderLooksForged() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));
        MockHttpServletRequest request = directRequest();
        request.addHeader("Host", "evil.example.com/login");

        String url = (String) inviteController.getInviteLink(session(), request).getData().get("inviteUrl");

        assertEquals("/login.html?invite=" + INVITE_CODE, url);
    }

    @Test
    void getInviteLink_fallsBackToRelativePathWithoutHostHeader() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));

        String url = (String) inviteController.getInviteLink(session(), new MockHttpServletRequest())
                .getData().get("inviteUrl");

        assertEquals("/login.html?invite=" + INVITE_CODE, url);
    }

    @Test
    void getInviteLink_ignoresForgedForwardedProto() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));
        MockHttpServletRequest request = directRequest();
        request.addHeader("Host", "tree.example.com");
        request.addHeader("X-Forwarded-Proto", "javascript:alert");

        String url = (String) inviteController.getInviteLink(session(), request).getData().get("inviteUrl");

        assertEquals("http://tree.example.com/login.html?invite=" + INVITE_CODE, url);
    }

    @Test
    void getInviteLink_missingCode_throwsBusinessException() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> inviteController.getInviteLink(session(), directRequest()));
        assertEquals("邀请码不可用，请在家族管理中刷新邀请码", ex.getMessage());
    }

    @Test
    void getInviteLink_familyMissing_throwsBusinessException() {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(null);

        assertThrows(BusinessException.class, () -> inviteController.getInviteLink(session(), directRequest()));
    }

    @Test
    void getInviteLink_withoutFamilyInSession_throwsBusinessException() {
        assertThrows(BusinessException.class, () ->
                inviteController.getInviteLink(new MockHttpSession(), directRequest()));
        verify(familyApplicationService, never()).getFamilyById(anyLong());
    }

    // ========== 邀请二维码 ==========

    @Test
    void getInviteQrCode_encodesSameInviteUrl() throws IOException, WriterException, ReaderException {
        when(familyApplicationService.getFamilyById(FAMILY_ID)).thenReturn(family(INVITE_CODE));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("tree.example.com");
        request.setServerPort(80);
        request.addHeader("Host", "tree.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Result<Map<String, Object>> link = inviteController.getInviteLink(session(), request);
        inviteController.getInviteQrCode(session(), request, response);

        assertEquals("image/png", response.getContentType());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.getContentAsByteArray()));
        com.google.zxing.Result decoded = new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
        assertEquals(link.getData().get("inviteUrl"), decoded.getText());
    }

    // ========== 辅助 ==========

    private MockHttpSession session() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(FamilyTreeConsts.SESSION_USER_ID, USER_ID);
        session.setAttribute(FamilyTreeConsts.SESSION_FAMILY_ID, FAMILY_ID);
        return session;
    }

    private MockHttpServletRequest directRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(80);
        return request;
    }

    private FamilyDTO family(String inviteCode) {
        FamilyDTO dto = new FamilyDTO();
        dto.setId(FAMILY_ID);
        dto.setName("测试家族");
        dto.setInviteCode(inviteCode);
        return dto;
    }
}
