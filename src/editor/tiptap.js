/* ============================================================================
   Tiptap 어댑터 — provider 교체 계약의 구현체 하나.

   공통 계약(provider 무관, fragments/editor.html 이 대외 창구):
     · 폼 안에 <textarea name="{field}"> 가 이미 있다. 그것이 저장되는 값이다.
     · 에디터는 그 textarea 를 감추고 그 자리에 편집 영역을 올린다.
     · <b>제출 직전에 편집 결과 HTML 을 textarea 에 써 넣는다.</b>
       폼 쪽 코드(th:field, 서버 바인딩)는 provider 를 전혀 모른다.

   설계 메모
     · 인라인 스크립트 없음 — 설정은 컨테이너의 data-* 로 받는다(CSP nonce 규약)
     · 이미지는 blob: 미리보기를 쓰지 않는다. img-src 에 blob: 이 없기 때문에(실측)
       업로드를 먼저 끝내고 반환된 /file/{id} URL 을 본문에 넣는다.
     · 최소 기능으로 시작한다 — 굵게·기울임·제목·목록·인용·링크·표·이미지.
       서버 정화(HtmlSanitizer) allowlist 밖의 것은 어차피 저장되지 않는다.
   ============================================================================ */
import { Editor } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Link from '@tiptap/extension-link';
import Image from '@tiptap/extension-image';
import { Table, TableRow, TableHeader, TableCell } from '@tiptap/extension-table';

/** 툴바 버튼 정의 — [명령키, 라벨, 접근성 이름]. 최소 구성. */
const BUTTONS = [
  ['bold', 'B', '굵게'],
  ['italic', 'I', '기울임'],
  ['h2', 'H2', '제목 2'],
  ['h3', 'H3', '제목 3'],
  ['bulletList', '••', '글머리 목록'],
  ['orderedList', '1.', '번호 목록'],
  ['blockquote', '""', '인용'],
  ['link', '🔗', '링크'],
  ['table', '⊞', '표 삽입'],
  ['image', '🖼', '이미지'],
  ['undo', '↶', '실행 취소'],
  ['redo', '↷', '다시 실행'],
];

function button(label, title, action) {
  const el = document.createElement('button');
  el.type = 'button';                       // 폼 안이므로 명시하지 않으면 submit 이 된다
  el.className = 'gp-editor-btn';
  el.textContent = label;
  el.title = title;
  el.setAttribute('aria-label', title);
  el.addEventListener('click', action);
  return el;
}

/** 이미지 업로드 — 공통 업로드 경로를 그대로 쓴다(에디터 전용 경로를 만들지 않는다). */
function uploadImage(root, file) {
  const form = new FormData();
  form.append('file', file);
  form.append('entityId', root.dataset.entityId || '');
  if (root.dataset.siteId) {
    form.append('siteId', root.dataset.siteId);
  }
  const token = (root.closest('form') || document).querySelector('input[name="_csrf"]');
  return fetch('/api/v1/file/image', {
    method: 'POST',
    headers: token ? { 'X-CSRF-TOKEN': token.value } : {},
    credentials: 'same-origin',
    body: form,
  }).then((res) => res.json().then((body) => {
    if (!res.ok) {
      throw new Error(body.message || '이미지를 올리지 못했습니다.');
    }
    return body;
  }));
}

function buildToolbar(root, editor) {
  const bar = document.createElement('div');
  bar.className = 'gp-editor-toolbar';
  bar.setAttribute('role', 'toolbar');
  bar.setAttribute('aria-label', '서식 도구');

  BUTTONS.forEach(([key, label, title]) => {
    bar.appendChild(button(label, title, () => {
      const chain = editor.chain().focus();
      switch (key) {
        case 'bold': chain.toggleBold().run(); break;
        case 'italic': chain.toggleItalic().run(); break;
        case 'h2': chain.toggleHeading({ level: 2 }).run(); break;
        case 'h3': chain.toggleHeading({ level: 3 }).run(); break;
        case 'bulletList': chain.toggleBulletList().run(); break;
        case 'orderedList': chain.toggleOrderedList().run(); break;
        case 'blockquote': chain.toggleBlockquote().run(); break;
        case 'undo': chain.undo().run(); break;
        case 'redo': chain.redo().run(); break;
        case 'table':
          // 머리행을 켠 채 만든다 — 표 머리셀은 접근성(KWCAG) 판정 대상이다
          chain.insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run();
          break;
        case 'link': {
          const prev = editor.getAttributes('link').href || '';
          const url = window.prompt('링크 주소', prev);
          if (url === null) { return; }
          if (url === '') { chain.unsetLink().run(); return; }
          chain.extendMarkRange('link').setLink({ href: url }).run();
          break;
        }
        case 'image': {
          const picker = document.createElement('input');
          picker.type = 'file';
          picker.accept = 'image/*';
          picker.addEventListener('change', () => {
            const file = picker.files && picker.files[0];
            if (!file) { return; }
            uploadImage(root, file)
              .then((saved) => {
                // 업로드가 끝난 뒤 URL 을 넣는다 — blob: 미리보기는 CSP 에 막힌다
                editor.chain().focus()
                  .setImage({ src: '/file/' + saved.fileId, alt: saved.originalName || '' })
                  .run();
              })
              .catch((e) => window.alert(e.message));
          });
          picker.click();
          break;
        }
        default: break;
      }
    }));
  });
  return bar;
}

/** 한 컨테이너를 에디터로 바꾼다. 이미 초기화됐으면 아무것도 하지 않는다(htmx 멱등). */
function mount(root) {
  if (root.dataset.initialized === 'true') { return; }
  const textarea = document.getElementById(root.dataset.target);
  if (!textarea) { return; }
  root.dataset.initialized = 'true';

  const area = document.createElement('div');
  area.className = 'gp-editor-area';

  const editor = new Editor({
    element: area,
    extensions: [
      StarterKit.configure({ link: false }),
      Link.configure({ openOnClick: false }),
      Image,
      Table.configure({ resizable: false }),
      TableRow, TableHeader, TableCell,
    ],
    content: textarea.value || '',
  });

  root.appendChild(buildToolbar(root, editor));
  root.appendChild(area);
  textarea.classList.add('gp-editor-source');   // 화면에서 감추되 폼 값으로는 남는다

  // 공통 계약 — 제출 직전에 결과를 textarea 로 되돌린다
  const form = root.closest('form');
  if (form) {
    form.addEventListener('submit', () => {
      const html = editor.getHTML();
      // editor.isEmpty 로 판단하면 안 된다 — 표나 이미지만 있는 본문을 '비어 있음' 으로
      // 보고 빈 값을 저장한다(실측: 표를 넣고 저장했더니 "내용을 입력해 주세요").
      // 정말 빈 문서일 때 ProseMirror 가 내놓는 형태만 빈 값으로 취급한다.
      textarea.value = (html === '<p></p>' || html === '<p><br></p>') ? '' : html;
    });
  }
}

function init() {
  document.querySelectorAll('[data-editor="tiptap"]').forEach(mount);
}

document.addEventListener('DOMContentLoaded', init);
document.addEventListener('htmx:load', init);
