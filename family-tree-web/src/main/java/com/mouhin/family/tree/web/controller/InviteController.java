package com.mouhin.family.tree.web.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 邀请控制器
 *
 * @author Family-Tree
 * @date 2026-08-28
 */
@RestController
@RequestMapping("/api/invite")
public class InviteController extends BaseController {

    /**
     * 获取邀请链接
     *
     * @param session HTTP 会话
     * @return 包含邀请码、邀请链接和家族ID的响应
     */
    @GetMapping("/link")
    public Result<Map<String, Object>> getInviteLink(HttpSession session) {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);

        // 生成邀请码（基于 familyId 和 userId 的 base36 编码）
        String inviteCode = generateInviteCode(familyId, userId);
        String inviteUrl = "/login.html?invite=" + inviteCode;

        Map<String, Object> data = new HashMap<>(4);
        data.put("inviteCode", inviteCode);
        data.put("inviteUrl", inviteUrl);
        data.put("familyId", familyId);

        return Result.success(data);
    }

    /**
     * 生成邀请二维码图片
     *
     * @param session  HTTP 会话
     * @param response HTTP 响应
     * @throws Exception 生成二维码时发生异常
     */
    @GetMapping("/qrcode")
    public void getInviteQrCode(HttpSession session, HttpServletResponse response) throws Exception {
        Long familyId = getCurrentFamilyId(session);
        Long userId = getCurrentUserId(session);

        String inviteCode = generateInviteCode(familyId, userId);
        String inviteUrl = "/login.html?invite=" + inviteCode;

        // 生成二维码
        QRCodeWriter writer = new QRCodeWriter();
        Map<EncodeHintType, Object> hints = new HashMap<>(4);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);

        BitMatrix bitMatrix = writer.encode(inviteUrl, BarcodeFormat.QR_CODE, 300, 300, hints);

        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-cache");
        OutputStream out = response.getOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
        out.flush();
    }

    /**
     * 生成邀请码
     *
     * @param familyId 家族ID
     * @param userId   用户ID
     * @return 邀请码字符串
     */
    private String generateInviteCode(Long familyId, Long userId) {
        long combined = familyId * 100000L + userId;
        return Long.toString(combined, 36).toUpperCase();
    }
}
