/**
 * 族谱前端 - AI 助手模块
 * 四大 AI 能力：智能录入、自然语言查询、家族故事生成、OCR 解析。
 *
 * @author Family-Tree
 * @date 2026-08-27
 */
(function () {
    'use strict';

    var FT = window.FT;

    /** 当前选中的 AI 功能标签 */
    var currentTab = 'smart-entry';

    /**
     * 功能标签配置
     */
    var TABS = [
        { id: 'smart-entry', label: '智能录入', icon: '&#9998;' },
        { id: 'query', label: '智能问答', icon: '&#10067;' },
        { id: 'story', label: '家族故事', icon: '&#128214;' },
        { id: 'ocr', label: 'OCR 识别', icon: '&#128247;' }
    ];

    /**
     * 打开 AI 助手弹窗
     */
    function showAiModal() {
        currentTab = 'smart-entry';
        renderModal();
        bindEvents();
    }

    /**
     * 渲染弹窗内容
     */
    function renderModal() {
        var tabsHtml = '<div class="ai-tabs">';
        TABS.forEach(function (tab) {
            var activeClass = tab.id === currentTab ? ' ai-tab-active' : '';
            tabsHtml += '<button class="ai-tab-btn' + activeClass + '" data-tab="' + tab.id + '">'
                + '<span class="ai-tab-icon">' + tab.icon + '</span>'
                + FT.escapeHtml(tab.label) + '</button>';
        });
        tabsHtml += '</div>';

        var bodyHtml = '';
        switch (currentTab) {
            case 'smart-entry':
                bodyHtml = renderSmartEntryTab();
                break;
            case 'query':
                bodyHtml = renderQueryTab();
                break;
            case 'story':
                bodyHtml = renderStoryTab();
                break;
            case 'ocr':
                bodyHtml = renderOcrTab();
                break;
        }

        var html = '<div class="ai-drag-handle" id="ai-drag-handle">'
            + '<h3 class="ai-modal-title">AI 族谱助手</h3>'
            + '</div>'
            + tabsHtml
            + '<div class="ai-tab-content">' + bodyHtml + '</div>'
            + '<div class="ai-modal-actions">'
            + '<button class="ai-close-btn" data-close-modal>关闭</button></div>';

        FT.showModal(html, 'modal-wide');
        initDrag();
    }

    /**
     * 智能录入标签页
     */
    function renderSmartEntryTab() {
        return '<div class="ai-form-section">'
            + '<p class="ai-form-desc">用自然语言描述家族人物和关系，AI 将自动解析为结构化数据。</p>'
            + '<p class="ai-form-example">示例：张三，男，1950年生，妻子李四，1952年生，儿子张小五，1980年生</p>'
            + '<textarea id="ai-smart-entry-input" class="ai-textarea" placeholder="请描述家族人物和关系..." rows="6" maxlength="5000"></textarea>'
            + '<div class="ai-form-footer">'
            + '<span class="ai-char-count" id="ai-se-char-count">0 / 5000</span>'
            + '<button class="ai-submit-btn" id="ai-smart-entry-btn">开始解析</button>'
            + '</div>'
            + '<div id="ai-smart-entry-result" class="ai-result-area" style="display:none;"></div>'
            + '</div>';
    }

    /**
     * 智能问答标签页
     */
    function renderQueryTab() {
        return '<div class="ai-form-section">'
            + '<p class="ai-form-desc">用自然语言提问关于族谱的问题，AI 将基于族谱数据回答。</p>'
            + '<p class="ai-form-example">示例：张三有几个孙子？家族中年龄最大的是谁？</p>'
            + '<textarea id="ai-query-input" class="ai-textarea" placeholder="请输入你的问题..." rows="3" maxlength="500"></textarea>'
            + '<div class="ai-form-footer">'
            + '<span class="ai-char-count" id="ai-q-char-count">0 / 500</span>'
            + '<button class="ai-submit-btn" id="ai-query-btn">提问</button>'
            + '</div>'
            + '<div id="ai-query-result" class="ai-result-area" style="display:none;"></div>'
            + '</div>';
    }

    /**
     * 家族故事标签页
     */
    function renderStoryTab() {
        return '<div class="ai-form-section">'
            + '<p class="ai-form-desc">选择族谱中的某位成员，AI 将为其生成人物传记或家族故事。</p>'
            + '<div class="ai-node-select-area">'
            + '<label class="ai-select-label">选择人物：</label>'
            + '<select id="ai-story-node-select" class="ai-select"><option value="">请选择...</option></select>'
            + '</div>'
            + '<div class="ai-form-footer">'
            + '<button class="ai-submit-btn" id="ai-story-btn">生成故事</button>'
            + '</div>'
            + '<div id="ai-story-result" class="ai-result-area" style="display:none;"></div>'
            + '</div>';
    }

    /**
     * OCR 识别标签页
     */
    function renderOcrTab() {
        return '<div class="ai-form-section">'
            + '<p class="ai-form-desc">上传老族谱图片，通过 OCR 识别文字后由 AI 解析为结构化数据。</p>'
            + '<div class="ai-upload-area" id="ai-ocr-upload-area">'
            + '<input type="file" id="ai-ocr-file-input" accept="image/*" style="display:none;">'
            + '<button class="ai-upload-btn" id="ai-ocr-upload-btn">&#128247; 选择图片</button>'
            + '<div id="ai-ocr-preview" class="ai-image-preview" style="display:none;"></div>'
            + '</div>'
            + '<textarea id="ai-ocr-text-input" class="ai-textarea" placeholder="OCR 识别结果将显示在这里，也可手动粘贴..." rows="6" maxlength="10000"></textarea>'
            + '<div class="ai-form-footer">'
            + '<span class="ai-char-count" id="ai-ocr-char-count">0 / 10000</span>'
            + '<button class="ai-submit-btn" id="ai-ocr-parse-btn">AI 解析</button>'
            + '</div>'
            + '<div id="ai-ocr-result" class="ai-result-area" style="display:none;"></div>'
            + '</div>';
    }

    /**
     * 绑定事件
     */
    function bindEvents() {
        // 标签切换
        document.querySelectorAll('.ai-tab-btn').forEach(function (btn) {
            btn.addEventListener('click', function () {
                currentTab = btn.dataset.tab;
                renderModal();
                bindEvents();
            });
        });

        // 字数统计
        bindCharCount('ai-smart-entry-input', 'ai-se-char-count', 5000);
        bindCharCount('ai-query-input', 'ai-q-char-count', 500);
        bindCharCount('ai-ocr-text-input', 'ai-ocr-char-count', 10000);

        // 智能录入
        var seBtn = document.getElementById('ai-smart-entry-btn');
        if (seBtn) {
            seBtn.addEventListener('click', handleSmartEntry);
        }

        // 智能问答
        var qBtn = document.getElementById('ai-query-btn');
        if (qBtn) {
            qBtn.addEventListener('click', handleQuery);
        }

        // 家族故事
        var sBtn = document.getElementById('ai-story-btn');
        if (sBtn) {
            populateNodeSelect();
            sBtn.addEventListener('click', handleStory);
        }

        // OCR 上传
        var uploadBtn = document.getElementById('ai-ocr-upload-btn');
        var fileInput = document.getElementById('ai-ocr-file-input');
        if (uploadBtn && fileInput) {
            uploadBtn.addEventListener('click', function () {
                fileInput.click();
            });
            fileInput.addEventListener('change', handleOcrImage);
        }

        // OCR 解析
        var ocrBtn = document.getElementById('ai-ocr-parse-btn');
        if (ocrBtn) {
            ocrBtn.addEventListener('click', handleOcrParse);
        }
    }

    /**
     * 绑定字数统计
     */
    function bindCharCount(inputId, countId, max) {
        var input = document.getElementById(inputId);
        var count = document.getElementById(countId);
        if (input && count) {
            input.addEventListener('input', function () {
                count.textContent = this.value.length + ' / ' + max;
            });
        }
    }

    /**
     * 处理智能录入
     */
    async function handleSmartEntry() {
        var input = document.getElementById('ai-smart-entry-input');
        var content = input.value.trim();
        if (!content) {
            FT.toast('请输入描述内容', 'warning');
            return;
        }
        var btn = document.getElementById('ai-smart-entry-btn');
        btn.disabled = true;
        btn.textContent = '解析中...';
        showResult('ai-smart-entry-result', '<div class="ai-loading">AI 正在解析，请稍候...</div>');

        try {
            var res = await FT.api('/api/ai/smart-entry', {
                method: 'POST',
                body: JSON.stringify({ description: content })
            });
            if (res.code === 200) {
                renderSmartEntryResult(res.data);
            } else {
                showResult('ai-smart-entry-result', '<div class="ai-error">' + FT.escapeHtml(res.message) + '</div>');
            }
        } catch (e) {
            showResult('ai-smart-entry-result', '<div class="ai-error">请求失败，请重试</div>');
        } finally {
            btn.disabled = false;
            btn.textContent = '开始解析';
        }
    }

    /**
     * 渲染智能录入结果
     */
    function renderSmartEntryResult(data) {
        var nodes = data.nodes || [];
        var relations = data.relations || [];
        var html = '<div class="ai-result-content">';
        html += '<h4 class="ai-result-title">解析结果</h4>';
        html += '<p class="ai-result-summary">共识别 <strong>' + nodes.length + '</strong> 位人物，<strong>' + relations.length + '</strong> 条关系</p>';

        if (nodes.length > 0) {
            html += '<div class="ai-result-section"><h5>人物列表</h5><ul class="ai-result-list">';
            nodes.forEach(function (n) {
                var genderText = n.gender === 1 ? '男' : n.gender === 2 ? '女' : '未知';
                html += '<li><strong>' + FT.escapeHtml(n.name) + '</strong>（' + genderText + '）';
                if (n.birthDate) { html += ' 生于 ' + FT.escapeHtml(n.birthDate); }
                if (n.deathDate) { html += ' 卒于 ' + FT.escapeHtml(n.deathDate); }
                if (n.zi) { html += ' 字' + FT.escapeHtml(n.zi); }
                if (n.hao) { html += ' 号' + FT.escapeHtml(n.hao); }
                html += '</li>';
            });
            html += '</ul></div>';
        }

        if (relations.length > 0) {
            html += '<div class="ai-result-section"><h5>关系列表</h5><ul class="ai-result-list">';
            relations.forEach(function (r) {
                var typeText = r.relationType === 1 ? '亲子' : r.relationType === 2 ? '夫妻' : '收养';
                html += '<li>' + FT.escapeHtml(r.fromName) + ' → ' + FT.escapeHtml(r.toName) + '（' + typeText + '）</li>';
            });
            html += '</ul></div>';
        }

        html += '<div class="ai-result-actions">'
            + '<button class="ai-confirm-btn" id="ai-se-confirm-btn">确认导入族谱</button>'
            + '</div></div>';

        showResult('ai-smart-entry-result', html);

        var confirmBtn = document.getElementById('ai-se-confirm-btn');
        if (confirmBtn) {
            confirmBtn.addEventListener('click', function () {
                importSmartEntry(data);
            });
        }
    }

    /**
     * 导入智能录入数据到族谱
     */
    async function importSmartEntry(data) {
        var nodes = data.nodes || [];
        var relations = data.relations || [];
        var imported = 0;
        var nameToId = {};

        FT.toast('正在导入...', 'info');

        try {
            // 先创建所有节点
            for (var i = 0; i < nodes.length; i++) {
                var n = nodes[i];
                var body = {
                    name: n.name,
                    gender: n.gender || 0
                };
                if (n.birthDate) { body.birthDate = n.birthDate; }
                if (n.deathDate) { body.deathDate = n.deathDate; }
                if (n.zi) { body.zi = n.zi; }
                if (n.hao) { body.hao = n.hao; }
                if (n.graveLocation) { body.graveLocation = n.graveLocation; }
                if (n.remark) { body.remark = n.remark; }

                var res = await FT.api('/api/node', {
                    method: 'POST',
                    body: JSON.stringify(body)
                });
                if (res.code === 200) {
                    nameToId[n.name] = res.data.id;
                    imported++;
                }
            }

            // 再创建关系
            for (var j = 0; j < relations.length; j++) {
                var r = relations[j];
                var fromId = nameToId[r.fromName];
                var toId = nameToId[r.toName];
                if (fromId && toId) {
                    var relBody = {
                        fromNodeId: fromId,
                        toNodeId: toId,
                        relationType: r.relationType
                    };
                    if (r.marriageDate) { relBody.marriageDate = r.marriageDate; }
                    await FT.api('/api/relation', {
                        method: 'POST',
                        body: JSON.stringify(relBody)
                    });
                }
            }

            FT.toast('成功导入 ' + imported + ' 位人物');
            if (FT.loadTree) { FT.loadTree(); }
            FT.closeModal();
        } catch (e) {
            FT.toast('导入失败：' + e.message, 'error');
        }
    }

    /**
     * 处理智能问答
     */
    async function handleQuery() {
        var input = document.getElementById('ai-query-input');
        var question = input.value.trim();
        if (!question) {
            FT.toast('请输入问题', 'warning');
            return;
        }
        var btn = document.getElementById('ai-query-btn');
        btn.disabled = true;
        btn.textContent = '思考中...';
        showResult('ai-query-result', '<div class="ai-loading">AI 正在思考，请稍候...</div>');

        try {
            var res = await FT.api('/api/ai/query', {
                method: 'POST',
                body: JSON.stringify({ question: question })
            });
            if (res.code === 200) {
                showResult('ai-query-result',
                    '<div class="ai-result-content">'
                    + '<h4 class="ai-result-title">回答</h4>'
                    + '<div class="ai-answer-text">' + FT.escapeHtml(res.data.answer).replace(/\n/g, '<br>') + '</div>'
                    + '</div>');
            } else {
                showResult('ai-query-result', '<div class="ai-error">' + FT.escapeHtml(res.message) + '</div>');
            }
        } catch (e) {
            showResult('ai-query-result', '<div class="ai-error">请求失败，请重试</div>');
        } finally {
            btn.disabled = false;
            btn.textContent = '提问';
        }
    }

    /**
     * 填充人物选择下拉框
     */
    function populateNodeSelect() {
        var select = document.getElementById('ai-story-node-select');
        if (!select) { return; }
        var treeData = FT.state.treeData || [];
        var names = [];
        function collectNames(nodes) {
            if (!nodes) { return; }
            nodes.forEach(function (n) {
                names.push({ id: n.id, name: n.name });
                collectNames(n.children);
                collectNames(n.spouses);
            });
        }
        collectNames(treeData);
        names.forEach(function (n) {
            var opt = document.createElement('option');
            opt.value = n.id;
            opt.textContent = n.name;
            select.appendChild(opt);
        });
    }

    /**
     * 处理家族故事生成
     */
    async function handleStory() {
        var select = document.getElementById('ai-story-node-select');
        var nodeId = select.value;
        if (!nodeId) {
            FT.toast('请选择人物', 'warning');
            return;
        }
        var btn = document.getElementById('ai-story-btn');
        btn.disabled = true;
        btn.textContent = '生成中...';
        showResult('ai-story-result', '<div class="ai-loading">AI 正在撰写故事，请稍候...</div>');

        try {
            var res = await FT.api('/api/ai/story', {
                method: 'POST',
                body: JSON.stringify({ nodeId: parseInt(nodeId, 10) })
            });
            if (res.code === 200) {
                showResult('ai-story-result',
                    '<div class="ai-result-content">'
                    + '<h4 class="ai-result-title">家族故事</h4>'
                    + '<div class="ai-story-text">' + FT.escapeHtml(res.data.story).replace(/\n/g, '<br>') + '</div>'
                    + '</div>');
            } else {
                showResult('ai-story-result', '<div class="ai-error">' + FT.escapeHtml(res.message) + '</div>');
            }
        } catch (e) {
            showResult('ai-story-result', '<div class="ai-error">请求失败，请重试</div>');
        } finally {
            btn.disabled = false;
            btn.textContent = '生成故事';
        }
    }

    /**
     * 处理 OCR 图片上传（前端 OCR 识别）
     */
    function handleOcrImage(e) {
        var file = e.target.files[0];
        if (!file) { return; }

        var preview = document.getElementById('ai-ocr-preview');
        var reader = new FileReader();
        reader.onload = function (ev) {
            preview.innerHTML = '<img src="' + ev.target.result + '" style="max-width:100%;max-height:200px;border-radius:6px;">';
            preview.style.display = 'block';
        };
        reader.readAsDataURL(file);

        // 提示用户：需要手动粘贴 OCR 结果或使用外部 OCR 工具
        FT.toast('请选择图片后，使用 OCR 工具识别文字并粘贴到文本框中', 'info');
    }

    /**
     * 处理 OCR 解析
     */
    async function handleOcrParse() {
        var input = document.getElementById('ai-ocr-text-input');
        var text = input.value.trim();
        if (!text) {
            FT.toast('请先输入 OCR 识别文本', 'warning');
            return;
        }
        var btn = document.getElementById('ai-ocr-parse-btn');
        btn.disabled = true;
        btn.textContent = '解析中...';
        showResult('ai-ocr-result', '<div class="ai-loading">AI 正在解析 OCR 文本，请稍候...</div>');

        try {
            var res = await FT.api('/api/ai/ocr-parse', {
                method: 'POST',
                body: JSON.stringify({ recognizedText: text })
            });
            if (res.code === 200) {
                renderSmartEntryResult(res.data);
                // 将结果也显示在 OCR 结果区域
                var ocrResult = document.getElementById('ai-ocr-result');
                if (ocrResult) {
                    ocrResult.style.display = 'block';
                }
            } else {
                showResult('ai-ocr-result', '<div class="ai-error">' + FT.escapeHtml(res.message) + '</div>');
            }
        } catch (e) {
            showResult('ai-ocr-result', '<div class="ai-error">请求失败，请重试</div>');
        } finally {
            btn.disabled = false;
            btn.textContent = 'AI 解析';
        }
    }

    /**
     * 显示结果区域
     */
    function showResult(elementId, html) {
        var el = document.getElementById(elementId);
        if (el) {
            el.innerHTML = html;
            el.style.display = 'block';
        }
    }

    /**
     * 初始化弹窗拖拽功能
     */
    function initDrag() {
        var handle = document.getElementById('ai-drag-handle');
        if (!handle) { return; }

        var modalEl = handle.closest('.modal');
        if (!modalEl) { return; }

        var overlay = modalEl.parentElement;
        var isDragging = false;
        var startX = 0;
        var startY = 0;
        var origLeft = 0;
        var origTop = 0;

        handle.addEventListener('mousedown', function (e) {
            if (e.target.closest('[data-close-modal]')) { return; }
            e.preventDefault();

            isDragging = true;
            startX = e.clientX;
            startY = e.clientY;

            // 将 modal 从 flex 居中切换为绝对定位以便自由移动
            var rect = modalEl.getBoundingClientRect();
            modalEl.style.position = 'fixed';
            modalEl.style.left = rect.left + 'px';
            modalEl.style.top = rect.top + 'px';
            modalEl.style.margin = '0';
            origLeft = rect.left;
            origTop = rect.top;

            // 取消 overlay 的 flex 居中
            overlay.style.display = 'block';

            document.body.classList.add('ai-modal-dragging');
        });

        document.addEventListener('mousemove', function (e) {
            if (!isDragging) { return; }
            var dx = e.clientX - startX;
            var dy = e.clientY - startY;
            var newLeft = origLeft + dx;
            var newTop = origTop + dy;

            // 限制在视口内
            var vw = window.innerWidth;
            var vh = window.innerHeight;
            var mw = modalEl.offsetWidth;
            var mh = modalEl.offsetHeight;
            newLeft = Math.max(0, Math.min(newLeft, vw - mw));
            newTop = Math.max(0, Math.min(newTop, vh - mh));

            modalEl.style.left = newLeft + 'px';
            modalEl.style.top = newTop + 'px';
        });

        document.addEventListener('mouseup', function () {
            if (!isDragging) { return; }
            isDragging = false;
            document.body.classList.remove('ai-modal-dragging');
        });
    }

    FT.showAiModal = showAiModal;
})();
