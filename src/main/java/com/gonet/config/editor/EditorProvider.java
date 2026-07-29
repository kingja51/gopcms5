package com.gonet.config.editor;

import java.util.List;

/**
 * 위지윅 provider — {@code gopcms.editor.provider} 로 고른다.
 *
 * <p>교체해도 흔들리지 않는 축(저장·정화·업로드·권한)은 provider 와 무관하게 <b>한 벌</b>이다.
 * provider 별로 갈리는 것은 세 가지뿐 — 자산 목록, 프래그먼트 이름, 어댑터 JS.
 *
 * <p>어댑터가 지켜야 할 공통 계약은 하나다: <b>제출 직전에 편집 결과 HTML 을
 * {@code <textarea name="{field}">} 에 써 넣는다.</b> 폼과 서버 바인딩은 provider 를 모른다.
 */
public enum EditorProvider {

    /** 기본. ProseMirror 기반 — 스키마 밖 마크업이 모델 단계에서 떨어져 서버 정화와 잘 맞는다. */
    TIPTAP("editor-tiptap",
            List.of("/js/vendor/editor-tiptap.js"),
            List.of("/css/editor-tiptap.css")),

    /** Namo CrossEditor 4 (상용) — 벤더 번들을 {@code static/js/vendor/crosseditor/} 에 둔다. */
    NAMO("editor-namo",
            List.of("/js/vendor/crosseditor/js/namo_scripteditor.js",
                    "/js/vendor/editor-namo.js"),
            List.of()),

    /** CKEditor 5 — GPL 로고 또는 상용 라이선스 키가 필요하다(도입 전 확인). */
    CKEDITOR5("editor-ckeditor5",
            List.of("/js/vendor/ckeditor5/ckeditor5.js",
                    "/js/vendor/editor-ckeditor5.js"),
            List.of("/js/vendor/ckeditor5/ckeditor5.css"));

    private final String fragment;
    private final List<String> scripts;
    private final List<String> styles;

    EditorProvider(String fragment, List<String> scripts, List<String> styles) {
        this.fragment = fragment;
        this.scripts = scripts;
        this.styles = styles;
    }

    /** {@code templates/fragments/editor/{name}.html} — 대외 창구는 editor.html 하나뿐이다. */
    public String fragment() {
        return fragment;
    }

    /** 이 provider 가 필요로 하는 자산. 기동 시 실재 여부를 검사한다(폼을 열고 알면 늦다). */
    public List<String> scripts() {
        return scripts;
    }

    public List<String> styles() {
        return styles;
    }
}
