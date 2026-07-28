package com.gonet.config.web;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 폼 문자열 바인딩 규칙 — 앞뒤 공백 제거 + <b>빈 문자열은 null 로</b>.
 *
 * <p>HTML 폼의 "선택 안 함" 옵션은 값을 보내지 않는 게 아니라 빈 문자열을 보낸다.
 * 그대로 두면 {@code template_id = ''} 같은 값이 만들어져 FK 위반(또는 "선택했는데
 * 선택 안 한 것으로 취급되지 않는" 상태)이 된다 — P7 실측: 3축을 "선택 안 함"으로
 * 저장하면 테마 검증에 걸려 저장이 거부됐다.
 *
 * <p>빈 문자열이 <b>의미를 갖는</b> 값(테마 css_class = '' 은 기본 브랜드)은 서비스가
 * null 을 다시 ''로 되돌린다 — 경계를 서비스에 두고, 바인딩 규칙은 하나로 유지한다.
 */
@ControllerAdvice
public class FormBinderAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }
}
