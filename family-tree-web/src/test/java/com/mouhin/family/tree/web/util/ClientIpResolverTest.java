package com.mouhin.family.tree.web.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 客户端 IP 解析单测：核心是「只有直连对端是本机 nginx 时才采信代理头」，
 * 以及采信时取 XFF 最右一段（客户端伪造的值只会出现在左边）。
 *
 * @author Family-Tree
 * @date 2026-11-24
 */
class ClientIpResolverTest {

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void nonLocalPeerWinsOverForgedHeaders() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        request.addHeader("X-Real-IP", "1.1.1.1");
        assertEquals("203.0.113.9", ClientIpResolver.resolve(request),
                "请求没经过本机 nginx 时，TCP 源地址不可伪造，头部一律不信");
    }

    @Test
    void localPeerPrefersXRealIpOverForwardedFor() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "203.0.113.7");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        assertEquals("203.0.113.7", ClientIpResolver.resolve(request),
                "nginx 用 proxy_set_header 覆盖写 X-Real-IP，那份是权威的");
    }

    @Test
    void localPeerFallsBackToRightmostForwardedForHop() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "9.9.9.9, 203.0.113.66");
        assertEquals("203.0.113.66", ClientIpResolver.resolve(request),
                "$proxy_add_x_forwarded_for 是「原值 + 追加真实对端」，最右一段才是真实来源");
    }

    @Test
    void singleHopForwardedForIsUsedAsIs() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.66");
        assertEquals("203.0.113.66", ClientIpResolver.resolve(request));
    }

    @Test
    void multiValueXRealIpIsRejectedAndFallsBackToHops() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "1.1.1.1, 2.2.2.2");
        request.addHeader("X-Forwarded-For", "203.0.113.20");
        assertEquals("203.0.113.20", ClientIpResolver.resolve(request),
                "X-Real-IP 出现逗号说明不是本机 nginx 写的那一份");
    }

    @Test
    void localPeerWithoutAnyProxyHeaderUsesLoopback() {
        assertEquals("127.0.0.1", ClientIpResolver.resolve(request("127.0.0.1")));
    }

    @Test
    void unknownMarkerIsNotTrustedAsAddress() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader("X-Real-IP", "unknown");
        assertEquals("127.0.0.1", ClientIpResolver.resolve(request));
    }

    @Test
    void linkLocalIpv6PeerCountsAsLocal() {
        MockHttpServletRequest request = request("fe80::1%eth0");
        request.addHeader("X-Real-IP", "203.0.113.31");
        assertEquals("203.0.113.31", ClientIpResolver.resolve(request));
    }

    @Test
    void nonLocalIpv6PeerIsNormalizedWithoutScope() {
        MockHttpServletRequest request = request("2001:DB8::1%eth0");
        request.addHeader("X-Forwarded-For", "8.8.8.8");
        assertEquals("2001:db8::1", ClientIpResolver.resolve(request));
    }

    @Test
    void missingRemoteAddrYieldsUnknown() {
        // MockHttpServletRequest 默认把 remoteAddr 填成 127.0.0.1，必须显式清空才能模拟容器未给出对端
        for (String absent : new String[] {null, "", "  "}) {
            MockHttpServletRequest request = request(absent);
            request.addHeader("X-Forwarded-For", "8.8.8.8");
            assertEquals(ClientIpResolver.UNKNOWN, ClientIpResolver.resolve(request),
                    "对端未知时头部一律不信，且返回值不能为 null: remoteAddr=" + absent);
        }
    }
}
