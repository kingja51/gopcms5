/* ============================================================================
   관리자 대시보드 차트 — Chart.js(자체호스팅) 렌더링 한 곳.
   ----------------------------------------------------------------------------
   설계 원칙
     · 데이터는 마크업의 data-chart-data(JSON) 에서 읽는다. 인라인 <script> 로 값을
       심지 않는 것은 CSP(script-src 'self' 'nonce-…') 규약을 우회하지 않기 위해서다.
     · 색은 KRDS 토큰을 CSS 변수에서 읽어온다. Chart.js 는 CSS 변수를 직접 못 쓰므로
       getComputedStyle 로 실제 값을 뽑아 넘긴다 — 테마(theme-*)를 바꿔도 차트가 따라온다.
     · htmx:load 에서도 다시 돌 수 있게 data-initialized 가드를 둔다(멱등).
   ========================================================================== */
(function () {
    'use strict';

    /** KRDS 토큰 실제 값 읽기 — 없으면 두 번째 인자로 폴백. */
    function token(name, fallback) {
        var v = getComputedStyle(document.documentElement).getPropertyValue(name);
        return (v && v.trim()) || fallback;
    }

    /** 차트 팔레트 — 브랜드 축을 먼저 쓰고, 항목이 많아지면 순환시킨다. */
    function palette() {
        return [
            token('--brand-50', '#246beb'),
            token('--color-green-50', '#00a05a'),
            token('--color-orange-50', '#f59f00'),
            token('--color-purple-50', '#7048e8'),
            token('--color-red-50', '#e5484d'),
            token('--color-cyan-50', '#0aa2c0'),
            token('--color-gray-50', '#8a8f97'),
            token('--color-pink-50', '#d6336c')
        ];
    }

    function colorsFor(count) {
        var base = palette();
        var out = [];
        for (var i = 0; i < count; i++) {
            out.push(base[i % base.length]);
        }
        return out;
    }

    function parse(canvas) {
        try {
            return JSON.parse(canvas.dataset.chartData || '[]');
        } catch (e) {
            return [];
        }
    }

    /** 값이 모두 0 이면 차트 대신 안내를 보여준다 — 빈 축만 있는 그림은 오해를 부른다. */
    function isEmpty(rows) {
        if (!rows.length) { return true; }
        for (var i = 0; i < rows.length; i++) {
            if (Number(rows[i].value) > 0) { return false; }
        }
        return false; // 값이 전부 0이어도 "0이 이어지는 추이" 는 의미가 있으므로 그린다
    }

    function baseOptions(horizontal) {
        var grid = token('--c-line', '#dde1e6');
        var fg = token('--c-fg-subtle', '#5c6169');
        return {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: horizontal ? 'y' : 'x',
            plugins: {
                legend: { display: false },
                tooltip: { displayColors: false }
            },
            scales: {
                x: {
                    ticks: { color: fg, precision: 0 },
                    grid: { color: grid, drawTicks: false }
                },
                y: {
                    beginAtZero: true,
                    ticks: { color: fg, precision: 0 },
                    grid: { color: grid, drawTicks: false }
                }
            }
        };
    }

    function build(canvas) {
        if (canvas.dataset.initialized === 'true') { return; }
        if (typeof window.Chart === 'undefined') { return; }

        var rows = parse(canvas);
        var kind = canvas.dataset.chart;
        var title = canvas.dataset.chartTitle || '';
        var labels = rows.map(function (r) { return r.label; });
        var values = rows.map(function (r) { return Number(r.value) || 0; });

        if (!rows.length) {
            canvas.dataset.initialized = 'true';
            var note = document.createElement('p');
            note.className = 'ladm-chart-empty text-body-sm';
            note.textContent = '표시할 데이터가 없습니다.';
            canvas.parentNode.replaceChild(note, canvas);
            return;
        }

        var horizontal = kind === 'horizontalBar';
        var type = (kind === 'horizontalBar') ? 'bar' : kind;
        var isRound = (type === 'doughnut' || type === 'pie');
        var brand = token('--brand-50', '#246beb');

        var dataset = { label: title, data: values, borderWidth: isRound ? 0 : 1 };
        if (isRound) {
            dataset.backgroundColor = colorsFor(values.length);
        } else if (type === 'line') {
            dataset.borderColor = brand;
            dataset.backgroundColor = brand;
            dataset.tension = 0.3;
            dataset.fill = false;
            dataset.pointRadius = 3;
        } else {
            dataset.backgroundColor = horizontal ? colorsFor(values.length) : brand;
            dataset.borderColor = 'transparent';
            dataset.borderRadius = 4;
        }

        var options = isRound
            ? {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { position: 'right', labels: { color: token('--c-fg', '#1f2124') } }
                }
            }
            : baseOptions(horizontal);

        new window.Chart(canvas, {
            type: type,
            data: { labels: labels, datasets: [dataset] },
            options: options
        });
        canvas.dataset.initialized = 'true';
    }

    function initAll() {
        var list = document.querySelectorAll('canvas[data-chart]');
        for (var i = 0; i < list.length; i++) {
            build(list[i]);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
    document.body.addEventListener('htmx:load', initAll);
})();
