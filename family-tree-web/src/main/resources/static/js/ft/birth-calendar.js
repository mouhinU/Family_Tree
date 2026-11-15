/**
 * 族谱前端 - 出生日期阳历/阴历录入组件
 *
 * 阳历为唯一真值：无论以阳历还是阴历录入，最终都归一化为 yyyy-MM-dd 阳历提交，
 * 农历字符串由后端以阳历权威推导。此处换算仅用于录入时的实时预览，前端库 lunar.js
 * 与后端 cn.6tail:lunar 同版本同换算表，保证预览与落库一致。
 *
 * @author Family-Tree
 * @date 2026-11-14
 */
(function () {
    'use strict';

    var FT = window.FT;

    /**
     * 出生日期字段 HTML：阳历/阴历两种录入模式共用一个"阳历真值"。
     * 面板内控件 id 由 solarId 派生，故同一时刻仅一个字段实例存在（弹窗互斥）。
     *
     * @param solarId 隐藏阳历真值输入框 id（type=date），提交时读取该值
     * @param lunarText 已有的农历字符串（无阳历时的遗留数据，仅作只读提示）
     * @return HTML 字符串
     */
    function buildField(solarId, lunarText) {
        var lunar = solarId + '-lc';
        var ly = lunar + '-y';
        var lm = lunar + '-m';
        var ld = lunar + '-d';
        var solarPreview = lunar + '-spv';
        var lunarPreview = solarId + '-lpv';
        var legacyTip = lunarText
            ? '<div class="bc-legacy" style="display:none;">原有农历（无阳历）：' + FT.escapeHtml(lunarText)
                + '，请在“阳历”页录入出生日期后自动换算</div>'
            : '';
        return '<div class="birth-cal">' +
            '<input type="hidden" id="' + solarId + '-mode" value="solar">' +
            '<div class="bc-toggle">' +
            '<button type="button" class="bc-chip active" data-cal-mode="solar">阳历</button>' +
            '<button type="button" class="bc-chip" data-cal-mode="lunar">阴历</button>' +
            '</div>' +
            '<div class="bc-panel" data-panel="solar">' +
            '<div class="bc-row">' +
            '<input type="date" id="' + solarId + '" class="bc-solar">' +
            '<span class="bc-derive">农历 <b id="' + lunarPreview + '">—</b></span>' +
            '</div>' +
            '</div>' +
            '<div class="bc-panel" data-panel="lunar" style="display:none;">' +
            '<div class="bc-row">' +
            '<input type="number" id="' + ly + '" class="bc-ly" min="1600" max="2200" placeholder="年" style="width:72px;">' +
            '<select id="' + lm + '" class="bc-lm" style="width:104px;"></select>' +
            '<select id="' + ld + '" class="bc-ld" style="width:96px;"></select>' +
            '</div>' +
            '<div class="bc-derive">对应阳历 <b id="' + solarPreview + '">—</b></div>' +
            '</div>' +
            legacyTip +
            '</div>';
    }

    /** 1–12 月的中文月名（取农历库，含"闰"约定与后端一致） */
    function monthNames() {
        if (monthNames.cache) { return monthNames.cache; }
        var arr = ['', '正', '二', '三', '四', '五', '六', '七', '八', '九', '十', '冬', '腊'];
        try {
            for (var m = 1; m <= 12; m++) {
                arr[m] = window.Lunar.fromYmd(2000, m, 1).getMonthInChinese();
            }
        } catch (e) { /* 库不可用时回落硬编码 */ }
        monthNames.cache = arr;
        return arr;
    }

    /** 初一…三十 的中文日名 */
    function dayNames() {
        if (dayNames.cache) { return dayNames.cache; }
        var arr = [];
        try {
            for (var d = 1; d <= 30; d++) {
                arr[d] = window.Lunar.fromYmd(2000, 1, d).getDayInChinese();
            }
        } catch (e) {
            arr = [];
            for (var i = 1; i <= 30; i++) { arr[i] = String(i); }
        }
        dayNames.cache = arr;
        return arr;
    }

    /** 该农历年是否有闰月（返回 1–12，无闰月或异常返回 0） */
    function leapMonthOf(year) {
        if (!year || !window.LunarYear) { return 0; }
        try { return window.LunarYear.fromYear(year).getLeapMonth(); } catch (e) { return 0; }
    }

    /** 该年该农历月的天数（29 或 30，无法判定时默认 30） */
    function daysInLunarMonth(year, month) {
        if (!year || !month || !window.LunarMonth) { return 30; }
        try {
            return window.LunarMonth.fromYm(year, month).getDayCount();
        } catch (e) { return 30; }
    }

    /** 生成月份下拉：1–12，若当年有闰月则追加"闰X"负值选项 */
    function fillMonthOptions(select, year) {
        var names = monthNames();
        var leap = leapMonthOf(year);
        var html = '';
        for (var m = 1; m <= 12; m++) {
            html += '<option value="' + m + '">' + FT.escapeHtml(names[m] + '月') + '</option>';
        }
        if (leap > 0) {
            html += '<option value="' + (-leap) + '">闰' + FT.escapeHtml(names[leap]) + '月</option>';
        }
        select.innerHTML = html;
    }

    /** 生成日下拉：按当月实际天数截断 */
    function fillDayOptions(select, year, month, keepDay) {
        var names = dayNames();
        var max = daysInLunarMonth(year, month);
        var html = '';
        for (var d = 1; d <= max; d++) {
            html += '<option value="' + d + '">' + FT.escapeHtml(names[d]) + '</option>';
        }
        select.innerHTML = html;
        if (keepDay && keepDay <= max) { select.value = String(keepDay); }
    }

    /** 阳历字符串（yyyy-MM-dd）→ 农历展示串（干支年+月+日），失败返回 null */
    function solarToLunarText(solarYMD) {
        if (!solarYMD || !window.Solar) { return null; }
        var p = solarYMD.split('-');
        if (p.length !== 3) { return null; }
        try {
            var l = window.Solar.fromYmd(+p[0], +p[1], +p[2]).getLunar();
            return l.getYearInGanZhi() + '年' + l.getMonthInChinese() + '月' + l.getDayInChinese();
        } catch (e) { return null; }
    }

    /** 农历三元组 → {solar:'yyyy-MM-dd', lunarText:'干支年月日'}，失败返回 null */
    function lunarToSolar(y, m, d) {
        if (!y || !m || !d || !window.Lunar) { return null; }
        try {
            var l = window.Lunar.fromYmd(+y, +m, +d);
            var s = l.getSolar();
            var mm = ('0' + s.getMonth()).slice(-2);
            var dd = ('0' + s.getDay()).slice(-2);
            return {
                solar: s.getYear() + '-' + mm + '-' + dd,
                lunarText: l.getYearInGanZhi() + '年' + l.getMonthInChinese() + '月' + l.getDayInChinese()
            };
        } catch (e) { return null; }
    }

    function q(id) { return document.getElementById(id); }

    /** 依据隐藏真值刷新只读预览（不回填输入，避免相互触发死循环） */
    function refreshPreviews(solarId) {
        var solarEl = q(solarId);
        q(solarId + '-lpv').textContent = solarEl.value ? (solarToLunarText(solarEl.value) || '—') : '—';
    }

    function syncLunarToSolar(solarId) {
        var r = lunarToSolar(q(solarId + '-lc-y').value, q(solarId + '-lc-m').value, q(solarId + '-lc-d').value);
        if (r) {
            q(solarId).value = r.solar;
            q(solarId + '-lc-spv').textContent = r.solar;
        } else {
            q(solarId).value = '';
            q(solarId + '-lc-spv').textContent = '日期无效';
        }
    }

    function syncSolarToLunar(solarId) {
        var val = q(solarId).value;
        if (val) {
            var p = val.split('-');
            try {
                var lunarObj = window.Solar.fromYmd(+p[0], +p[1], +p[2]).getLunar();
                var y = lunarObj.getYear(), m = lunarObj.getMonth(), d = lunarObj.getDay();
                q(solarId + '-lc-y').value = y;
                fillMonthOptions(q(solarId + '-lc-m'), y);
                q(solarId + '-lc-m').value = String(m);
                fillDayOptions(q(solarId + '-lc-d'), y, m, d);
                q(solarId + '-lc-spv').textContent = val;
            } catch (e) { /* 忽略非法回填 */ }
        } else {
            q(solarId + '-lc-spv').textContent = '—';
        }
        refreshPreviews(solarId);
    }

    function switchMode(solarId, mode) {
        q(solarId + '-mode').value = mode;
        // 使用容器内查询，避免全局同 id 冲突
        var root = q(solarId).closest('.birth-cal');
        var allChips = root.querySelectorAll('.bc-chip');
        for (var i = 0; i < allChips.length; i++) {
            allChips[i].classList.toggle('active', allChips[i].getAttribute('data-cal-mode') === mode);
        }
        root.querySelector('[data-panel="solar"]').style.display = mode === 'solar' ? '' : 'none';
        root.querySelector('[data-panel="lunar"]').style.display = mode === 'lunar' ? '' : 'none';
        if (mode === 'lunar') {
            syncSolarToLunar(solarId);
        } else {
            if (q(solarId + '-lc-y').value) { syncLunarToSolar(solarId); }
            refreshPreviews(solarId);
        }
    }

    /**
     * 绑定交互并回填初始值。须在 buildField 的 HTML 插入 DOM 后调用。
     *
     * @param solarId 隐藏真值输入框 id
     * @param opts    {solar, lunar} 初始阳历/农历（其一存在即可）
     */
    function init(solarId, opts) {
        opts = opts || {};
        var root = q(solarId).closest('.birth-cal');
        var ly = q(solarId + '-lc-y'), lm = q(solarId + '-lc-m'), ld = q(solarId + '-lc-d');

        // 初始值：优先阳历；仅农历则回填农历三元组并切到阴历模式
        if (opts.solar) {
            q(solarId).value = opts.solar;
        }
        fillMonthOptions(lm, opts.solarYear || 2000);
        fillDayOptions(ld, opts.solarYear || 2000, 1, null);

        if (opts.solar) {
            syncSolarToLunar(solarId);
            switchMode(solarId, 'solar');
        } else if (opts.lunar) {
            var legacy = root.querySelector('.bc-legacy');
            if (legacy) { legacy.style.display = ''; }
            switchMode(solarId, 'lunar');
        } else {
            refreshPreviews(solarId);
        }

        // 事件绑定
        var chips = root.querySelectorAll('.bc-chip');
        for (var i = 0; i < chips.length; i++) {
            (function (chip) {
                chip.addEventListener('click', function () {
                    switchMode(solarId, chip.getAttribute('data-cal-mode'));
                });
            })(chips[i]);
        }
        q(solarId).addEventListener('change', function () {
            syncSolarToLunar(solarId);
        });
        q(solarId).addEventListener('input', function () {
            syncSolarToLunar(solarId);
        });
        ly.addEventListener('input', function () {
            fillMonthOptions(lm, +ly.value);
            var m = +lm.value;
            fillDayOptions(ld, +ly.value, m, +ld.value);
            syncLunarToSolar(solarId);
        });
        lm.addEventListener('change', function () {
            fillDayOptions(ld, +ly.value, +lm.value, null);
            syncLunarToSolar(solarId);
        });
        ld.addEventListener('change', function () {
            syncLunarToSolar(solarId);
        });
    }

    /**
     * 读取归一化后的阳历真值。
     *
     * @param solarId 隐藏真值输入框 id
     * @return {ok:boolean, solar:string|null, message?:string}
     */
    function getSolar(solarId) {
        var el = q(solarId);
        if (!el || !el.value) { return { ok: true, solar: null }; }
        var mode = q(solarId + '-mode').value;
        if (mode === 'lunar') {
            var r = lunarToSolar(q(solarId + '-lc-y').value, q(solarId + '-lc-m').value, q(solarId + '-lc-d').value);
            if (!r) { return { ok: false, solar: null, message: '农历日期无效，请检查年/月/日' }; }
            return { ok: true, solar: r.solar };
        }
        return { ok: true, solar: el.value };
    }

    FT.birthCalendar = {
        buildField: buildField,
        init: init,
        getSolar: getSolar
    };
})();
