/* ============================================================================
 * gopcms5 공통 스크립트 — 인라인 스크립트 금지 규약(외부 .js, self-host).
 * CSP nonce 는 P6(Security) 에서 적용 — 그 전까지 nonce 미부착 상태로 동작.
 *
 * 규약 (CLAUDE.md §UI):
 *  · 이벤트 위임: document 1회 등록 + closest('[data-action]')
 *  · htmx 조각 초기화는 htmx:load 에서 멱등 처리(data-initialized 가드)
 * ==========================================================================*/
(function () {
  'use strict';

  /* 고대비(hc) 복원 — FOUC-free nonce 인라인 복원은 P6 에서. 현재는 로드 시 복원(짧은 플래시 허용) */
  var HC_KEY = 'gopcms5.hc';
  try {
    if (localStorage.getItem(HC_KEY) === '1') {
      document.documentElement.classList.add('hc');
    }
  } catch (ignore) { /* 프라이빗 모드 등 storage 불가 환경 무시 */ }

  /* 전역 클릭 위임 */
  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-action]');
    if (!el) return;

    switch (el.dataset.action) {
      case 'toggle-hc': {
        var on = document.documentElement.classList.toggle('hc');
        try { localStorage.setItem(HC_KEY, on ? '1' : '0'); } catch (ignore) {}
        el.setAttribute('aria-pressed', String(on));
        break;
      }
      case 'open-dialog': { /* 네이티브 <dialog> — ESC/포커스 트랩 기본 제공 */
        var dlg = document.querySelector(el.dataset.target);
        if (dlg && typeof dlg.showModal === 'function') dlg.showModal();
        break;
      }
      case 'close-dialog': {
        var host = el.closest('dialog');
        if (host) host.close();
        break;
      }
      case 'scroll-top':
        window.scrollTo({ top: 0, behavior: 'smooth' });
        break;
      default:
        break;
    }
  });

  /* htmx 로 로드된 조각의 요소 초기화 — 멱등(data-initialized 가드) */
  document.addEventListener('htmx:load', function (e) {
    var root = e.detail && e.detail.elt ? e.detail.elt : document;
    root.querySelectorAll('[data-init]:not([data-initialized])').forEach(function (el) {
      el.setAttribute('data-initialized', '1');
      /* data-init 값별 초기화 분기 — 필요 시 여기에 추가 */
    });
  });
})();
