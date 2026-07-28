package com.gonet.config.view;

import com.gonet.config.CacheConfig;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 물리 뷰 템플릿 존재 검사 — Caffeine {@code viewExists} 캐시 (1시간).
 *
 * <p>키가 "레이아웃코드+뷰명" 물리 경로라 템플릿·레이아웃 전환 시 무효화 불필요.
 * ※ 개발 중 새 오버라이드 html 을 추가하면 캐시 TTL 전까지 폴백이 유지될 수 있다 —
 * 재기동으로 해소 (DevTools 금지 규약상 재기동은 일상 동작).
 */
@Component
public class ViewTemplateLookup {

    /** name 예: {@code layouts/layout-003/board/list} → templates/…/list.html 존재 여부. */
    @Cacheable(cacheNames = CacheConfig.VIEW_EXISTS)
    public boolean exists(String name) {
        return new ClassPathResource("templates/" + name + ".html").exists();
    }
}
