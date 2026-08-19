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

    FT.showModal = showModal;
    FT.closeModal = closeModal;
})();
