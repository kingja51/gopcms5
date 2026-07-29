package com.gonet.config.editor;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 선택된 provider 의 자산이 실제로 있는지 기동 시 확인한다.
 *
 * <p>없으면 <b>기동을 멈춘다</b>. 폼을 열고 나서야 "에디터가 안 뜬다" 를 알게 되면 늦고,
 * 그때는 이미 운영 중이다. {@code LayoutSmokeRunner}(레이아웃 뷰 검사)와 같은 방식이다.
 *
 * <p>특히 상용 번들(Namo·CKEditor)은 라이선스 때문에 저장소에 커밋하지 않는다 —
 * provider 만 바꾸고 번들을 안 넣은 상태가 실제로 일어난다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EditorAssetSmokeRunner implements ApplicationRunner {

    private final EditorProperties properties;
    private final ResourceLoader resourceLoader;

    @Override
    public void run(ApplicationArguments args) {
        EditorProvider provider = properties.getProvider();
        List<String> missing = new ArrayList<>();

        List<String> assets = new ArrayList<>(provider.scripts());
        assets.addAll(provider.styles());
        for (String path : assets) {
            Resource resource = resourceLoader.getResource("classpath:/static" + path);
            if (!resource.exists()) {
                missing.add(path);
            }
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "위지윅 provider '%s' 의 자산이 없습니다: %s — gopcms.editor.provider 를 바꾸거나 "
                            .formatted(provider.name().toLowerCase(), missing)
                            + "해당 번들을 static 아래에 배치하세요"
                            + (provider == EditorProvider.TIPTAP ? " (npm run editor)" : ""));
        }
        log.info("EditorSmoke OK — provider={} 자산 {}건 확인",
                provider.name().toLowerCase(), assets.size());
    }
}
