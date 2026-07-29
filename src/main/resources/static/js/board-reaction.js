/* ============================================================================
   좋아요 · 신고 — 레이아웃 7종의 반응 밴드와 게시판 화면이 함께 쓴다.

   · 인라인 스크립트 없음: data-action 위임(document 1회) — CSP 규약
   · htmx:load 멱등 초기화 대상이 아니다(전역 위임이라 재바인딩이 필요 없다)
   · 숫자는 서버 응답으로만 갱신한다. 클라이언트가 ±1 하면 다른 사람이 누른 사이에 어긋난다
   ============================================================================ */
(function () {
  'use strict';

  /* 토큰은 밴드가 스스로 들고 있다(프래그먼트 안 hidden) — 레이아웃마다 메타 태그를
     심어야 하는 방식은 하나만 빠뜨려도 조용히 403 이 된다. file-picker.js 와 같은 원천. */
  function csrf(band) {
    var input = (band || document).querySelector('input[name="_csrf"]')
      || document.querySelector('input[name="_csrf"]');
    return input ? input.value : '';
  }

  function post(url, params, band) {
    var headers = { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' };
    headers['X-CSRF-TOKEN'] = csrf(band);
    return fetch(url, {
      method: 'POST',
      headers: headers,
      credentials: 'same-origin',
      body: new URLSearchParams(params).toString()
    }).then(function (res) {
      return res.json().catch(function () { return {}; })
        .then(function (body) { return { ok: res.ok, status: res.status, body: body }; });
    });
  }

  /* 로그인이 필요한 동작이다 — 401 은 실패가 아니라 '로그인하면 된다' 는 안내다 */
  function handleAuth(result, siteCode) {
    if (result.status === 401 || result.status === 403) {
      var next = '/login' + (siteCode ? '?siteCode=' + encodeURIComponent(siteCode) : '');
      if (window.confirm('로그인이 필요합니다. 로그인 화면으로 이동할까요?')) {
        window.location.href = next;
      }
      return true;
    }
    return false;
  }

  function toggleLike(button) {
    var band = button.closest('[data-reaction]');
    if (!band || button.disabled) { return; }
    button.disabled = true;

    post('/api/v1/board/like', {
      targetType: band.getAttribute('data-target-type'),
      targetId: band.getAttribute('data-target-id'),
      sourceUrl: window.location.pathname
    }, band).then(function (result) {
      if (handleAuth(result, band.getAttribute('data-site-code'))) { return; }
      if (!result.ok) {
        window.alert(result.body.message || '처리하지 못했습니다.');
        return;
      }
      var count = band.querySelector('[data-like-count]');
      if (count) { count.textContent = result.body.count; }
      button.setAttribute('aria-pressed', result.body.liked ? 'true' : 'false');
      button.classList.toggle('is-on', !!result.body.liked);
    }).finally(function () {
      button.disabled = false;
    });
  }

  function submitReport(form) {
    var band = form.closest('[data-reaction]');
    var dialog = form.closest('dialog');
    var reason = form.querySelector('[name="reasonCode"]');
    if (!reason || !reason.value) {
      window.alert('신고 사유를 선택해 주세요.');
      return;
    }
    var detail = form.querySelector('[name="reasonText"]');

    post('/api/v1/board/report', {
      targetType: band.getAttribute('data-target-type'),
      targetId: band.getAttribute('data-target-id'),
      reasonCode: reason.value,
      reasonText: detail ? detail.value : '',
      sourceUrl: window.location.pathname
    }, band).then(function (result) {
      if (handleAuth(result, band.getAttribute('data-site-code'))) { return; }
      // 409(중복 신고)도 사용자에게는 그냥 안내다 — 서버 메시지를 그대로 보여준다
      window.alert(result.body.message || '처리하지 못했습니다.');
      if (dialog && result.ok) { dialog.close(); }
    });
  }

  document.addEventListener('click', function (e) {
    var el = e.target.closest('[data-action]');
    if (!el) { return; }
    switch (el.getAttribute('data-action')) {
      case 'like-toggle':
        e.preventDefault();
        toggleLike(el);
        break;
      case 'report-submit':
        e.preventDefault();
        submitReport(el.closest('form') || el.closest('dialog'));
        break;
      default:
        break;
    }
  });
})();
