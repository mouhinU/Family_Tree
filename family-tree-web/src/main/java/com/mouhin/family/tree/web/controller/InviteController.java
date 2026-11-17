package com.mouhin.family.tree.web.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.mouhin.family.tree.application.service.FamilyApplicationService;
import com.mouhin.family.tree.common.dto.FamilyDTO;
import com.mouhin.family.tree.common.exception.BusinessException;
import com.mouhin.family.tree.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

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
     * 邀请落地页路径，注册表单据此预填邀请码
     */
    private static final String INVITE_PAGE = "/login.html";

    /**
     * 二维码边长（像素）
     */
    private static final int QR_SIZE = 300;

    /**
     * 二维码留白（模块数）
     */
    private static final int QR_MARGIN = 2;

    /**
     * 可信 Host 形态：域名或 IPv4 加可选端口，用于过滤伪造的请求头
     */
    private static final Pattern SAFE_HOST_PATTERN = Pattern.compile("^[A-Za-z0-9.\\-]+(:\\d{1,5})?$");

    private final FamilyApplicationService familyApplicationService;

    public InviteController(FamilyApplicationService familyApplicationService) {
        this.familyApplicationService = familyApplicationService;
    }

    /**
     * 获取邀请链接
     *
     * @param session HTTP 会话
     * @param request HTTP 请求，用于还原可外部访问的站点地址
     * @return 包含邀请码、邀请链接和家族ID的响应
     */
    @GetMapping("/link")
    public Result<Map<String, Object>> getInviteLink(HttpSession session, HttpServletRequest request) {
        Long familyId = getCurrentFamilyId(session);
        String inviteCode = currentInviteCode(familyId);

        Map<String, Object> data = new HashMap<>(4);
        data.put("inviteCode", inviteCode);
        data.put("inviteUrl", inviteUrl(request, inviteCode));
        data.put("familyId", familyId);

        return Result.success(data);
    }

    /**
     * 生成邀请二维码图片
     *
     * @param session  HTTP 会话
     * @param request  HTTP 请求，用于还原可外部访问的站点地址
     * @param response HTTP 响应
     * @throws WriterException 二维码编码失败
     * @throws IOException     写出图片失败
     */
    @GetMapping("/qrcode")
    public void getInviteQrCode(HttpSession session, HttpServletRequest request, HttpServletResponse response)
            throws WriterException, IOException {
        String inviteUrl = inviteUrl(request, currentInviteCode(getCurrentFamilyId(session)));

        Map<EncodeHintType, Object> hints = new HashMap<>(4);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, QR_MARGIN);

        BitMatrix bitMatrix = new QRCodeWriter().encode(inviteUrl, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        response.setContentType("image/png");
        response.setHeader("Cache-Control", "no-cache");
        OutputStream out = response.getOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
        out.flush();
    }

    /**
     * 邀请码一律取库内真实码，与注册时加入家族的校验口径保持一致
     *
     * @param familyId 会话中的家族ID
     * @return 邀请码
     */
    private String currentInviteCode(Long familyId) {
        FamilyDTO family = familyApplicationService.getFamilyById(familyId);
        if (family == null || family.getInviteCode() == null || family.getInviteCode().isBlank()) {
            throw new BusinessException("邀请码不可用，请在家族管理中刷新邀请码");
        }
        return family.getInviteCode();
    }

    private String inviteUrl(HttpServletRequest request, String inviteCode) {
        return originOf(request) + INVITE_PAGE + "?invite=" + inviteCode;
    }

    /**
     * 还原外部可访问的站点地址：反向代理下 scheme 取 X-Forwarded-Proto、host 取 Host 头，
     * 否则二维码与复制链接会带上门禁容器的内网地址而无法使用；Host 形态可疑或缺失时
     * 不猜测站点地址，退回站内相对路径。
     *
     * @param request HTTP 请求
     * @return 形如 {@code https://host[:port]} 的来源地址，无法判定时为空串
     */
    private String originOf(HttpServletRequest request) {
        String host = request.getHeader("Host");
        if (host == null || !SAFE_HOST_PATTERN.matcher(host.trim()).matches()) {
            return "";
        }
        return normalizeScheme(request.getHeader("X-Forwarded-Proto"), request.getScheme()) + "://" + host.trim();
    }

    private String normalizeScheme(String forwardedScheme, String fallback) {
        if (forwardedScheme == null) {
            return fallback;
        }
        String single = forwardedScheme.trim().split(",")[0].toLowerCase(Locale.ROOT);
        return ("http".equals(single) || "https".equals(single)) ? single : fallback;
    }
}
