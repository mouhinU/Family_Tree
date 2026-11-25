package com.mouhin.family.tree.web.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 归一解析。
 *
 * <p>本机的应用永远只经 nginx 反代对外（`nginx-family-tree.conf` 把请求从 127.0.0.1 转到应用端口），
 * 所以 `getRemoteAddr()` 对所有真实用户都会是环回地址。要拿到可信的客户端 IP 只能回头部，
 * 而头部能不能信**取决于直连对端是不是本机 nginx**：</p>
 *
 * <ul>
 *   <li>对端非环回/链路本地 → 请求没有经过我们的 nginx（本机直调、集成测试、或应用直接暴露）。
 *       此时 TCP 源地址无法伪造，直接用它，并**忽略客户端自带的头部**。</li>
 *   <li>对端是环回/链路本地 → 采信 nginx 自己写的头。优先 {@code X-Real-IP}
 *       （nginx 用 {@code proxy_set_header} 覆盖写，客户端那份会被丢掉），其次取
 *       {@code X-Forwarded-For} 的**最右一段**：nginx 用 {@code $proxy_add_x_forwarded_for}
 *       是「原值 + 追加真实对端」，客户端伪造的那些值只会在左边。</li>
 * </ul>
 *
 * <p>历史口径是「XFF 取最左」，那等于让客户端自选身份：伪造一个 XFF 就能绕过按 IP 的
 * 短信额度与登录限流，并把 {@code operation_log.ip_address} 写成任意值。</p>
 *
 * @author Family-Tree
 * @date 2026-11-24
 */
public final class ClientIpResolver {

    /** 无法确定来源时的占位值，保证调用方永远拿到非空、可作 Redis key 的字符串 */
    public static final String UNKNOWN = "unknown";

    private ClientIpResolver() {
    }

    /**
     * 解析客户端 IP，永不返回 null。
     *
     * @param request HTTP 请求
     * @return 归一后的 IP；取不到时为 {@link #UNKNOWN}
     */
    public static String resolve(HttpServletRequest request) {
        String peer = normalize(request.getRemoteAddr());
        if (!isLocalPeer(peer)) {
            return peer;
        }
        String realIp = singleValue(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            String last = singleValue(hops[hops.length - 1]);
            if (last != null) {
                return last;
            }
        }
        return peer;
    }

    /**
     * 直连对端是否是本机进程（环回或链路本地），即「请求来自本机 nginx」的判据。
     */
    private static boolean isLocalPeer(String ip) {
        if (ip == null || UNKNOWN.equals(ip)) {
            return false;
        }
        return ip.startsWith("127.") || ip.startsWith("::1") || ip.startsWith("0:0:0:0:0:0:0:1")
                || ip.startsWith("169.254.") || ip.startsWith("fe80:");
    }

    /**
     * 取头部的单一值并归一；多值（客户端塞了逗号）或空值一律视为不可信。
     */
    private static String singleValue(String headerValue) {
        if (headerValue == null) {
            return null;
        }
        String trimmed = headerValue.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed) || trimmed.contains(",")) {
            return null;
        }
        return normalize(trimmed);
    }

    /** 去掉 IPv6 作用域后缀（{@code fe80::1%eth0}），统一小写 */
    private static String normalize(String ip) {
        if (ip == null) {
            return UNKNOWN;
        }
        String trimmed = ip.trim();
        if (trimmed.isEmpty()) {
            return UNKNOWN;
        }
        int scope = trimmed.indexOf('%');
        if (scope > 0) {
            trimmed = trimmed.substring(0, scope);
        }
        return trimmed.toLowerCase();
    }
}
