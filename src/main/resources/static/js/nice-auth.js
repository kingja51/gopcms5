/*
 * NICE 본인인증 — 팝업/부모창 신호 처리.
 *
 * 한 파일이 세 역할을 한다(어느 쪽인지는 DOM 의 data-* 속성이 정한다):
 *   [data-nice-launch] 팝업 1단계 — NICE 로 자동 POST
 *   [data-nice-result] 팝업 2단계 — 부모창에 완료를 알리고 창을 닫음
 *   [data-nice-open]   부모창    — 팝업을 열고 완료 신호를 기다림
 *
 * 부모에게 알리는 경로가 셋인 이유: COOP(same-origin) 환경에서 팝업이 NICE 도메인을
 * 거치고 오면 window.opener 가 끊긴다. localStorage 의 storage 이벤트와
 * BroadcastChannel 은 opener 관계와 무관하게 same-origin 두 창을 잇는다.
 * 셋 중 무엇이 먼저 도착하든 한 번만 처리한다.
 */
(function () {
    'use strict';

    var SIGNAL_KEY = 'GOPCMS_NICE_DONE';
    var CHANNEL = 'gopcms-nice';

    /* ── 공통: 닫기 버튼 ─────────────────────────────────────────────── */
    document.addEventListener('click', function (e) {
        var closer = e.target.closest('[data-nice-close]');
        if (closer) {
            window.close();
        }
        var opener = e.target.closest('[data-nice-open]');
        if (opener) {
            e.preventDefault();
            openPopup(opener.getAttribute('data-nice-open'));
        }
    });

    /* ── 팝업 1단계: NICE 로 자동 POST ───────────────────────────────── */
    var launch = document.querySelector('[data-nice-launch]');
    if (launch && launch.getAttribute('data-auto') === 'Y') {
        var form = document.forms.niceForm;
        // 사용자가 버튼을 누르지 않아도 진행한다 — 이 창 자체가 이미 사용자의 클릭 결과다
        if (form) {
            form.submit();
        }
    }

    /* ── 팝업 2단계: 완료 신호 + 자동 닫기 ───────────────────────────── */
    var result = document.querySelector('[data-nice-result]');
    if (result && result.getAttribute('data-success') === 'Y') {
        notifyParent(result.getAttribute('data-next'));
        window.close();
    }

    /* ── 부모창: 팝업 열기 + 완료 대기 ───────────────────────────────── */
    if (document.querySelector('[data-nice-open]')) {
        listenForCompletion();
    }

    function openPopup(url) {
        if (!url) {
            return;
        }
        window.open(url, 'gopcmsNice',
            'width=500,height=550,menubar=no,status=no,toolbar=no,scrollbars=yes');
    }

    function notifyParent(nextUrl) {
        try {
            if (window.opener && !window.opener.closed) {
                window.opener.location.href = nextUrl;
            }
        } catch (e) {
            /* COOP 로 opener 가 끊긴 경우 — 아래 폴백이 받는다 */
        }
        try {
            localStorage.setItem(SIGNAL_KEY, JSON.stringify({next: nextUrl, ts: Date.now()}));
            // 값을 남겨 두면 다음 인증에서 옛 신호를 다시 읽는다
            setTimeout(function () { localStorage.removeItem(SIGNAL_KEY); }, 500);
        } catch (e) {
            /* 시크릿 모드 등 — 저장 불가 */
        }
        try {
            if (typeof BroadcastChannel !== 'undefined') {
                var bc = new BroadcastChannel(CHANNEL);
                bc.postMessage({type: 'done', next: nextUrl});
                bc.close();
            }
        } catch (e) {
            /* 미지원 브라우저 */
        }
    }

    function listenForCompletion() {
        var handled = false;

        function go(nextUrl) {
            if (handled || !nextUrl) {
                return;
            }
            handled = true;
            window.location.href = nextUrl;
        }

        window.addEventListener('storage', function (e) {
            if (e.key !== SIGNAL_KEY || !e.newValue) {
                return;
            }
            try {
                go(JSON.parse(e.newValue).next);
            } catch (err) {
                /* 깨진 값 무시 */
            }
        });

        try {
            if (typeof BroadcastChannel !== 'undefined') {
                new BroadcastChannel(CHANNEL).addEventListener('message', function (ev) {
                    if (ev.data && ev.data.type === 'done') {
                        go(ev.data.next);
                    }
                });
            }
        } catch (e) {
            /* 미지원 브라우저 — storage 이벤트로 충분하다 */
        }
    }
})();
