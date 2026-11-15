/**
 * 族谱前端 - Service Worker 注册模块
 *
 * 独立成文件而非内联 <script>：CSP 的 script-src 未开放 'unsafe-inline'，内联注册块会被浏览器拦截。
 *
 * @author Family-Tree
 * @date 2026-11-15
 */
(function () {
    'use strict';

    /** Service Worker 脚本地址 */
    var SW_URL = '/sw.js';

    if (!('serviceWorker' in navigator)) {
        return;
    }

    window.addEventListener('load', function () {
        navigator.serviceWorker.register(SW_URL).catch(function () {
            // 注册失败（如非安全上下文）不影响主流程，静默忽略
        });
    });
})();
