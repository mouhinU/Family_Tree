package com.mouhin.family.tree.web.controller.user;

import com.mouhin.family.tree.application.service.FamilyApplicationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 邀请短链控制器：将公开邀请码转换为登录页地址。
 *
 * @author Family-Tree
 * @date 2026-11-27
 */
@RestController
public class ShortLinkController {

    private static final String INVITE_LOGIN_PATH = "/login.html";

    private final FamilyApplicationService familyApplicationService;

    public ShortLinkController(FamilyApplicationService familyApplicationService) {
        this.familyApplicationService = familyApplicationService;
    }

    @GetMapping("/s/{inviteCode}")
    public ResponseEntity<Void> redirectToInviteLogin(@PathVariable String inviteCode) {
        String canonicalInviteCode = familyApplicationService.getCanonicalInviteCode(inviteCode);
        if (canonicalInviteCode == null) {
            return ResponseEntity.notFound().build();
        }

        URI location = UriComponentsBuilder.fromPath(INVITE_LOGIN_PATH)
                .queryParam("invite", canonicalInviteCode)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(location)
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
