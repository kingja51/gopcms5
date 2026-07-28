package com.gonet.config.view;

import com.gonet.primary.site.dto.SiteContext;
import com.gonet.primary.site.service.SiteService;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 기동 스모크 — "선택한 템플릿이 적용되지 않았는데 화면은 뜨는" 침묵 실패 차단
 * (template-resolver-design.md §6.3).
 *
 * <p>활성 사이트가 참조하는 레이아웃의 {@code layout.html}(폴백 금지 파일)과
 * 템플릿 CSS({@code /tmpl/css/{code}.css}) 존재를 검증 — 누락 시 기동 중단.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayoutSmokeRunner implements ApplicationRunner {

    private final SiteService siteService;

    @Override
    public void run(ApplicationArguments args) {
        Set<String> missing = new LinkedHashSet<>();
        for (SiteContext context : siteService.getAllActiveContexts()) {
            String layoutFile = "templates/layouts/%s/layout.html".formatted(context.getLayoutCode());
            String cssFile = "static/tmpl/css/%s.css".formatted(context.getTemplateCode());
            if (!new ClassPathResource(layoutFile).exists()) {
                missing.add("[%s] %s".formatted(context.getSiteCode(), layoutFile));
            }
            if (!new ClassPathResource(cssFile).exists()) {
                missing.add("[%s] %s".formatted(context.getSiteCode(), cssFile));
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "레이아웃/템플릿 자원 누락 — 기동 중단 (layout.html 은 폴백 금지):\n  "
                            + String.join("\n  ", missing));
        }
        log.info("LayoutSmoke OK — 활성 사이트의 layout.html·템플릿 CSS 존재 확인 완료");
    }
}
