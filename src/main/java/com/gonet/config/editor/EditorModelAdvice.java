package com.gonet.config.editor;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 뷰에 provider 정보를 실어 준다 — 폼 화면은 {@code fragments/editor :: editor(...)} 만
 * 부르고 어느 provider 인지 몰라야 한다.
 *
 * <p>모델 이름을 짧게 두는 이유: 프래그먼트가 {@code ${editorFragment}} 하나로 분기하고
 * 나머지(자산 목록)는 레이아웃의 scripts 슬롯에서 쓰기 때문이다.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class EditorModelAdvice {

    private final EditorProperties properties;

    @ModelAttribute("editorProvider")
    public String editorProvider() {
        return properties.getProvider().name().toLowerCase();
    }

    @ModelAttribute("editorFragment")
    public String editorFragment() {
        return "fragments/editor/" + properties.getProvider().fragment();
    }

    @ModelAttribute("editorScripts")
    public java.util.List<String> editorScripts() {
        return properties.getProvider().scripts();
    }

    @ModelAttribute("editorStyles")
    public java.util.List<String> editorStyles() {
        return properties.getProvider().styles();
    }
}
