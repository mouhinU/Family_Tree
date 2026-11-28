package com.mouhin.family.tree.web.controller;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.web.controller.user.ShortLinkController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邀请短链控制器单元测试。
 *
 * @author Family-Tree
 * @date 2026-11-27
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkControllerTest {

    private static final String INVITE_CODE = "665AYXS6";

    @Mock
    private FamilyApplicationService familyApplicationService;

    private ShortLinkController shortLinkController;

    @BeforeEach
    void setUp() {
        shortLinkController = new ShortLinkController(familyApplicationService);
    }

    @Test
    void redirectToInviteLogin_validCode_returnsFoundWithLoginLocation() {
        when(familyApplicationService.getCanonicalInviteCode(INVITE_CODE)).thenReturn(INVITE_CODE);

        ResponseEntity<Void> response = shortLinkController.redirectToInviteLogin(INVITE_CODE);

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create("/login.html?invite=" + INVITE_CODE), response.getHeaders().getLocation());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verify(familyApplicationService).getCanonicalInviteCode(INVITE_CODE);
    }

    @Test
    void redirectToInviteLogin_lowercaseCode_usesCanonicalCodeFromDatabase() {
        when(familyApplicationService.getCanonicalInviteCode("665ayxs6")).thenReturn(INVITE_CODE);

        ResponseEntity<Void> response = shortLinkController.redirectToInviteLogin("665ayxs6");

        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(URI.create("/login.html?invite=" + INVITE_CODE), response.getHeaders().getLocation());
    }

    @Test
    void redirectToInviteLogin_unknownCode_returnsNotFound() {
        when(familyApplicationService.getCanonicalInviteCode("UNKNOWN1")).thenReturn(null);

        ResponseEntity<Void> response = shortLinkController.redirectToInviteLogin("UNKNOWN1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getHeaders().getLocation());
    }
}
