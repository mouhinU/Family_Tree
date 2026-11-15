/**
 * 族谱前端 - Service Worker 离线缓存
 *
 * 缓存策略与 index.html 的 ?v= cache-busting 约定配套：
 *   - 导航与页面本身：network-first，发版后立即生效，离线时才回落已缓存页面。
 *   - 带 ?v= 的 /js/ 与 /css/：cache-first，cache key 保留查询串，版本串一变即自然 miss 并回源。
 *   - 未带 ?v= 的 /js/ 与 /css/：network-first，避免无版本号的资源被永久钉住。
 *   - /api/ 与跨域请求：不拦截，避免把登录态、CSRF token 等敏感响应写入磁盘缓存。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
'use strict';

const CACHE_VERSION = 'v2';
const STATIC_CACHE = `ft-static-${CACHE_VERSION}`;
const PAGE_CACHE = `ft-page-${CACHE_VERSION}`;

/** 业务接口前缀，一律不走缓存 */
const API_PATH_PREFIX = '/api/';

/** 允许离线缓存的静态资源目录 */
const CACHEABLE_PATH_PREFIXES = ['/js/', '/css/'];

/** 静态资源的版本号查询参数 */
const VERSION_QUERY_PARAM = 'v';

/** 离线兜底页缓存键 */
const OFFLINE_FALLBACK_URL = '/index.html';

self.addEventListener('install', (event) => {
    event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then((keys) => Promise.all(
                keys
                    .filter((key) => key !== STATIC_CACHE && key !== PAGE_CACHE)
                    .map((key) => caches.delete(key))
            ))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', (event) => {
    const request = event.request;
    if (request.method !== 'GET') {
        return;
    }

    const url = new URL(request.url);
    if (url.origin !== self.location.origin || url.pathname.startsWith(API_PATH_PREFIX)) {
        return;
    }

    if (request.mode === 'navigate' || url.pathname === OFFLINE_FALLBACK_URL) {
        // 仅应用入口断网时回落首页外壳；其它页面兜底成外壳会触发登录页与首页互相跳转的死循环
        const fallbackUrl = isAppEntry(url.pathname) ? OFFLINE_FALLBACK_URL : null;
        event.respondWith(networkFirst(request, PAGE_CACHE, fallbackUrl));
        return;
    }

    if (isCacheableStatic(url.pathname)) {
        event.respondWith(url.searchParams.has(VERSION_QUERY_PARAM)
            ? cacheFirstAsset(request)
            : networkFirst(request, STATIC_CACHE, null));
    }
});

/**
 * 判断是否为 manifest 中 start_url 指向的应用入口
 */
function isAppEntry(pathname) {
    return pathname === '/' || pathname === OFFLINE_FALLBACK_URL;
}

/**
 * 判断路径是否属于可离线缓存的静态资源
 */
function isCacheableStatic(pathname) {
    return CACHEABLE_PATH_PREFIXES.some((prefix) => pathname.startsWith(prefix));
}

/**
 * 网络优先：回源成功后按请求自身 URL 更新缓存，断网时回落缓存（可选再兜底到兜底页）
 *
 * 兜底页只读不写：若把每次导航都写进兜底键，未登录时被客户端跳转的 family-setup.html 会污染兜底页内容。
 *
 * @param {Request} request 原始请求
 * @param {string} cacheName 写入的缓存名
 * @param {string} fallbackUrl 断网时的兜底缓存键，为空则只按请求本身兜底
 */
async function networkFirst(request, cacheName, fallbackUrl) {
    try {
        const response = await fetch(request);
        if (response.ok) {
            const cache = await caches.open(cacheName);
            await cache.put(request, response.clone());
        }
        return response;
    } catch (e) {
        const cached = await caches.match(request);
        return cached || (fallbackUrl ? caches.match(fallbackUrl) : undefined) || Response.error();
    }
}

/**
 * 静态资源：优先取缓存。默认 cache key 含查询串，因此 ?v= 变化即为新版本
 */
async function cacheFirstAsset(request) {
    const cached = await caches.match(request);
    if (cached) {
        return cached;
    }
    const response = await fetch(request);
    if (response.ok) {
        const cache = await caches.open(STATIC_CACHE);
        await cache.put(request, response.clone());
    }
    return response;
}
