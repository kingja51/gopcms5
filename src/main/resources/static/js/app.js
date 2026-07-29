/* ============================================================================
 * gopcms5 공통 스크립트 — 인라인 스크립트 금지 규약(외부 .js, self-host).
 * CSP: script-src 'self' 'nonce-…' (SecurityHeadersFilter) — 이 파일은 self 로 허용된다.
 * 유일한 인라인 예외는 각 레이아웃 <head> 의 hc 복원 스니펫(FOUC 방지, nonce 부착).
 *
 * 규약 (CLAUDE.md §UI):
 *  · 이벤트 위임: document 1회 등록 + closest('[data-action]')
 *  · htmx 조각 초기화는 htmx:load 에서 멱등 처리(data-initialized 가드)
 * ==========================================================================*/
(function () {
  'use strict';

  /* 복원은 레이아웃 <head> 의 nonce 인라인이 첫 페인트 전에 끝낸다 — 여기선 토글만 담당 */
  var HC_KEY = 'gopcms5.hc';

  /* 되돌릴 수 없는 조작(삭제 등) 확인 — 인라인 onclick 금지 규약의 대체 경로.
     data-confirm="문구" 를 붙이면 취소 시 제출 자체가 일어나지 않는다. */
  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-confirm]');
    if (el && !window.confirm(el.dataset.confirm)) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);

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
      case 'copy-text': {
        /* 링크 복사 — 값은 data-copy 에 담는다.
           navigator.clipboard 는 보안 컨텍스트(HTTPS·localhost)에서만 동작하므로,
           안 되는 환경에서는 선택 상태로 만들어 사용자가 직접 복사하게 둔다. */
        var text = el.dataset.copy || '';
        var done = function () {
          var old = el.textContent;
          el.textContent = '복사됨';
          setTimeout(function () { el.textContent = old; }, 1200);
        };
        if (navigator.clipboard && window.isSecureContext) {
          navigator.clipboard.writeText(text).then(done, function () {});
        } else {
          var src = el.previousElementSibling;
          if (src && typeof src.select === 'function') { src.select(); }
        }
        break;
      }
      default:
        break;
    }
  });

  /* 선택 즉시 조회 — 인라인 onchange 금지 규약(CSP)의 대체 경로.
     data-submit-on-change 를 단 컨트롤은 값이 바뀌면 소속 폼을 제출한다. */
  document.addEventListener('change', function (e) {
    var el = e.target.closest('[data-submit-on-change]');
    if (el && el.form) el.form.submit();
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
