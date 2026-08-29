/**
 * 族谱前端 - 弹窗基座模块
 * 模态框的打开与关闭，事件委托（data-close-modal）。
 * 所有子模块通过 FT.showModal / FT.closeModal 调用。
 *
 * @author Family-Tree
 * @date 2026-08-09
 */
(function () {
    'use strict';

    var FT = window.FT;

    // ========== 模态框基座 ==========
    function showModal(html, wide) {
        var sizeClass = typeof wide === 'string' ? wide : (wide ? 'modal-wide' : '');
        var closeBtn = '<button class="modal-close" data-close-modal title="关闭">&times;</button>';
        var modalContainer = document.getElementById('modal-container');
        modalContainer.innerHTML = '<div class="modal-overlay"><div class="modal' + (sizeClass ? ' ' + sizeClass : '') + '">' + closeBtn + html + '</div></div>';
        modalContainer.querySelector('.modal-overlay').addEventListener('click', function (e) {
            if (e.target === this) closeModal();
        });
        // 事件委托：所有 data-close-modal 元素触发关闭
        modalContainer.querySelectorAll('[data-close-modal]').forEach(function (el) {
            el.addEventListener('click', function () { closeModal(); });
        });
    }

    function closeModal() {
        document.getElementById('modal-container').innerHTML = '';
    }

    // ========== 确认弹窗 ==========
    // 统一的二次确认弹窗，替代浏览器原生 confirm()。
    // 渲染在独立遮罩层上（z-index 高于普通弹窗），不影响已打开的弹窗。
    // options 可选：{ title, confirmText, cancelText }
    function showConfirm(message, onConfirm, options) {
        if (document.getElementById('confirm-container')) { return; }
        var opts = options || {};
        var confirmText = opts.confirmText || '确定';
        var cancelText = opts.cancelText || '取消';
        var titleHtml = opts.title ? '<h3>' + FT.escapeHtml(opts.title) + '</h3>' : '';

        var overlay = document.createElement('div');
        overlay.id = 'confirm-container';
        overlay.className = 'modal-overlay confirm-overlay';
        overlay.innerHTML = '<div class="modal modal-confirm">' +
            titleHtml +
            '<p class="confirm-message">' + FT.escapeHtml(message || '') + '</p>' +
            '<div class="modal-actions">' +
            '<button class="btn-cancel" id="confirm-cancel">' + cancelText + '</button>' +
            '<button class="btn-confirm" id="confirm-ok">' + confirmText + '</button>' +
            '</div></div>';
        document.body.appendChild(overlay);

        function close() {
            overlay.remove();
        }

        overlay.addEventListener('click', function (e) {
            if (e.target === this) { close(); }
        });
        overlay.querySelector('#confirm-cancel').addEventListener('click', close);
        overlay.querySelector('#confirm-ok').addEventListener('click', function () {
            close();
            if (typeof onConfirm === 'function') { onConfirm(); }
        });
    }

    FT.showModal = showModal;
    FT.closeModal = closeModal;
    FT.confirm = showConfirm;
})();
