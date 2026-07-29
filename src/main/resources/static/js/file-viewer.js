/* ============================================================================
   공통 문서 뷰어 — fragments/file-viewer.html 의 동작.
   ----------------------------------------------------------------------------
   지원 범위는 업로드 화이트리스트(any)가 정한다(서버가 판정해 data-viewer 로 준다).
   형식마다 여는 주체가 다르다:
     · IMAGE  <img>
     · VIDEO  <video> (브라우저 코덱에 맡긴다)
     · TEXT   원문을 그대로 표시 — 해석하지 않는다(textContent)
     · PDF / OFFICE  <iframe> (오피스는 서버가 미리 PDF 로 바꿔 둔다)
     · HWP    rhwp(WASM)로 브라우저 안에서 파싱 → SVG
   서버가 신뢰할 수 없는 문서를 여는 것은 오피스 변환 하나뿐이고, 나머지는 전부
   브라우저(또는 WASM 샌드박스)가 연다 — 파서 취약점의 영향 범위를 줄이려는 설계다.

   rhwp 는 7MB WASM 이라 <b>HWP 를 실제로 열 때만</b> 내려받는다(동적 import).
   ========================================================================== */
(function () {
    'use strict';

    var RHWP_URL = '/js/vendor/rhwp/rhwp.js';
    var PDFJS_URL = '/js/vendor/pdfjs/pdf.min.mjs';
    var PDFJS_WORKER = '/js/vendor/pdfjs/pdf.worker.min.mjs';
    // 한글 PDF 는 CMap 과 표준 글꼴 데이터가 있어야 글자가 그려진다.
    // 없으면 오류 없이 '빈 페이지' 가 되어 원인을 찾기 어렵다(실측으로 잡은 함정).
    var PDFJS_CMAPS = '/js/vendor/pdfjs/cmaps/';
    var PDFJS_FONTS = '/js/vendor/pdfjs/standard_fonts/';
    var rhwpReady = null;               // 모듈 로딩은 한 번만
    var pdfjsReady = null;

    function status(root, message) {
        var stage = root.querySelector('.gp-viewer-stage');
        stage.innerHTML = '';
        var p = document.createElement('p');
        p.className = 'gp-viewer-status text-body-sm';
        p.textContent = message;
        stage.appendChild(p);
    }

    function stageOf(root) {
        var stage = root.querySelector('.gp-viewer-stage');
        stage.innerHTML = '';
        return stage;
    }

    /* ── 이미지 ─────────────────────────────────────────────────────────── */
    function renderImage(root) {
        var img = document.createElement('img');
        img.className = 'gp-viewer-image';
        img.alt = root.dataset.fileName || '';
        img.addEventListener('error', function () {
            status(root, '이미지를 불러오지 못했습니다.');
        });
        img.src = root.dataset.src;
        stageOf(root).appendChild(img);
    }

    /* ── 영상 ───────────────────────────────────────────────────────────── */
    function renderVideo(root) {
        var video = document.createElement('video');
        video.className = 'gp-viewer-video';
        video.controls = true;
        video.preload = 'metadata';       // 목록에서 여러 건이 통째로 내려오지 않게
        video.addEventListener('error', function () {
            // 코덱을 브라우저가 모를 수 있다 — 재생 실패는 파일 문제가 아닐 수 있다
            status(root, '이 영상은 브라우저에서 재생할 수 없습니다. 내려받아 확인해 주세요.');
        });
        video.src = root.dataset.src;
        stageOf(root).appendChild(video);
    }

    /* ── 텍스트 (txt · csv) ─────────────────────────────────────────────── */
    var TEXT_LIMIT = 512 * 1024;        // 큰 로그 파일을 통째로 그리면 브라우저가 멈춘다

    function renderText(root) {
        status(root, '문서를 불러오는 중입니다…');
        fetch(root.dataset.raw, { credentials: 'same-origin' }).then(function (res) {
            if (!res.ok) { throw new Error('fetch ' + res.status); }
            return res.text();
        }).then(function (text) {
            var truncated = text.length > TEXT_LIMIT;
            var pre = document.createElement('pre');
            pre.className = 'gp-viewer-text';
            // textContent 로 넣는다 — 파일 안에 태그가 있어도 마크업으로 해석되지 않는다.
            // innerHTML 이었다면 .txt 하나로 XSS 가 된다.
            pre.textContent = truncated ? text.slice(0, TEXT_LIMIT) : text;
            var stage = stageOf(root);
            stage.appendChild(pre);
            if (truncated) {
                var note = document.createElement('p');
                note.className = 'gp-viewer-status text-body-xs';
                note.textContent = '문서가 길어 앞부분만 표시했습니다. 전체는 내려받아 확인해 주세요.';
                stage.appendChild(note);
            }
        }).catch(function () {
            status(root, '문서를 불러오지 못했습니다.');
        });
    }

    /* ── PDF · 오피스(변환된 PDF) ───────────────────────────────────────────
       브라우저 내장 뷰어(iframe)를 쓰지 않는 이유:
         ① sandbox 를 채운 iframe 에서는 내장 PDF 뷰어가 아예 뜨지 않는다(실측: 빈 화면).
            sandbox 를 풀면 문서에게 스크립트·플러그인 자리를 내주는 셈이라 본말이 전도된다.
         ② pdf.js 는 우리 JS 가 파싱해 canvas 로 그린다. 문서 안의 JavaScript 는
            기본적으로 실행되지 않으므로, 오히려 플러그인에 넘기는 것보다 통제가 쉽다.
       워커에서 파싱하므로 큰 문서를 열어도 화면이 멈추지 않는다. */
    function loadPdfjs() {
        if (!pdfjsReady) {
            pdfjsReady = import(PDFJS_URL).then(function (mod) {
                mod.GlobalWorkerOptions.workerSrc = PDFJS_WORKER;
                return mod;
            });
        }
        return pdfjsReady;
    }

    function renderPdf(root) {
        status(root, '문서를 여는 중입니다…');
        loadPdfjs().then(function (pdfjs) {
            return pdfjs.getDocument({
                url: root.dataset.src,
                withCredentials: true,
                isEvalSupported: false,     // 문서가 가진 스크립트를 실행할 여지를 없앤다
                cMapUrl: PDFJS_CMAPS,
                cMapPacked: true,
                standardFontDataUrl: PDFJS_FONTS
            }).promise;
        }).then(function (pdf) {
            var stage = stageOf(root);
            var pageBox = document.createElement('div');
            pageBox.className = 'gp-viewer-page-box';
            var canvas = document.createElement('canvas');
            pageBox.appendChild(canvas);
            stage.appendChild(pageBox);

            var total = pdf.numPages;
            var current = 1;
            var pager = root.querySelector('.gp-viewer-pager');
            var label = root.querySelector('.gp-viewer-page');
            var task = null;             // 진행 중인 렌더 — 페이지를 바꾸면 취소한다
            pager.hidden = total <= 1;

            function draw() {
                // 앞선 렌더가 아직 돌고 있으면 끊는다. 그대로 두면 두 페이지가
                // 같은 캔버스에 겹쳐 그려지고, 늦게 끝난 쪽이 화면을 차지한다.
                if (task) { task.cancel(); task = null; }
                var pageNo = current;
                pdf.getPage(pageNo).then(function (page) {
                    if (pageNo !== current) { return; }      // 그 사이 또 바뀌었다
                    // 무대 폭에 맞춘다 — 고정 배율이면 좁은 화면에서 잘린다
                    var base = page.getViewport({ scale: 1 });
                    var scale = Math.min((stage.clientWidth - 32) / base.width, 2);
                    var viewport = page.getViewport({ scale: scale > 0 ? scale : 1 });
                    canvas.width = viewport.width;
                    canvas.height = viewport.height;
                    task = page.render({ canvasContext: canvas.getContext('2d'), viewport: viewport });
                    label.textContent = pageNo + ' / ' + total;
                    // 완료를 기다린다 — 기다리지 않으면 "그리는 중" 상태가 완료처럼 보인다
                    return task.promise.then(function () { task = null; }, function () { /* 취소 */ });
                });
            }

            root.addEventListener('click', function (e) {
                var btn = e.target.closest('[data-action]');
                if (!btn || !root.contains(btn)) { return; }
                if (btn.dataset.action === 'viewer-prev' && current > 1) { current--; draw(); }
                if (btn.dataset.action === 'viewer-next' && current < total) { current++; draw(); }
            });
            draw();
        }).catch(function (e) {
            status(root, '이 문서는 미리보기를 만들 수 없습니다. 내려받아 확인해 주세요.');
            if (window.console) { console.warn('pdf viewer', e); }
        });
    }

    /* ── HWP · HWPX (rhwp WASM) ─────────────────────────────────────────── */
    function loadRhwp() {
        if (!rhwpReady) {
            rhwpReady = import(RHWP_URL).then(function (mod) {
                return mod.default().then(function () { return mod; });
            });
        }
        return rhwpReady;
    }

    function renderHwp(root) {
        status(root, '한글 문서를 여는 중입니다…');
        Promise.all([
            loadRhwp(),
            // 원본 바이트를 그대로 받는다 — 서버는 이 파일을 열지 않는다
            fetch(root.dataset.raw, { credentials: 'same-origin' }).then(function (res) {
                if (!res.ok) { throw new Error('fetch ' + res.status); }
                return res.arrayBuffer();
            })
        ]).then(function (both) {
            var mod = both[0];
            var bytes = new Uint8Array(both[1]);
            var doc = new mod.HwpDocument(bytes);
            var viewer = new mod.HwpViewer(doc);
            var total = viewer.pageCount();
            if (!total) { throw new Error('empty document'); }

            var stage = stageOf(root);
            var pageBox = document.createElement('div');
            pageBox.className = 'gp-viewer-page-box';
            stage.appendChild(pageBox);

            var current = 1;
            var pager = root.querySelector('.gp-viewer-pager');
            var label = root.querySelector('.gp-viewer-page');
            pager.hidden = total <= 1;

            function draw() {
                // renderPageSvg 는 문자열 SVG 를 준다. innerHTML 로 넣지 않고
                // 파싱해 넣는 이유: 문서에서 온 마크업을 우리 문서 컨텍스트에
                // 그대로 붙이지 않기 위해서다(스크립트 노드는 걸러낸다).
                var svgText = viewer.renderPageSvg(current);
                var parsed = new DOMParser().parseFromString(svgText, 'image/svg+xml');
                var svg = parsed.documentElement;
                if (!svg || svg.nodeName === 'parsererror') {
                    status(root, '이 문서는 미리보기를 만들 수 없습니다.');
                    return;
                }
                svg.querySelectorAll('script, foreignObject').forEach(function (n) {
                    n.remove();
                });
                pageBox.innerHTML = '';
                pageBox.appendChild(document.importNode(svg, true));
                label.textContent = current + ' / ' + total;
            }

            root.addEventListener('click', function (e) {
                var btn = e.target.closest('[data-action]');
                if (!btn || !root.contains(btn)) { return; }
                if (btn.dataset.action === 'viewer-prev' && current > 1) { current--; draw(); }
                if (btn.dataset.action === 'viewer-next' && current < total) { current++; draw(); }
            });
            draw();
        }).catch(function (e) {
            // 어떤 문서가 파서를 어떻게 흔드는지는 화면에 알리지 않는다
            status(root, '이 문서는 미리보기를 만들 수 없습니다. 내려받아 확인해 주세요.');
            if (window.console) { console.warn('hwp viewer', e); }
        });
    }

    function init(root) {
        if (root.dataset.initialized === 'true') { return; }
        root.dataset.initialized = 'true';
        switch (root.dataset.viewer) {
            case 'IMAGE': renderImage(root); break;
            case 'VIDEO': renderVideo(root); break;
            case 'TEXT': renderText(root); break;
            case 'PDF':
            case 'OFFICE': renderPdf(root); break;
            case 'HWP': renderHwp(root); break;
            default:
                status(root, '이 형식은 미리보기를 지원하지 않습니다. 내려받아 확인해 주세요.');
        }
    }

    function initAll() {
        document.querySelectorAll('.gp-viewer[data-viewer]').forEach(init);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }
    document.addEventListener('htmx:load', initAll);
})();
