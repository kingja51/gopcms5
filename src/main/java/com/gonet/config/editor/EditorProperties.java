package com.gonet.config.editor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 위지윅 설정 — {@code gopcms.editor.*}.
 *
 * <pre>
 * gopcms:
 *   editor:
 *     provider: tiptap      # tiptap | namo | ckeditor5
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "gopcms.editor")
public class EditorProperties {

    private EditorProvider provider = EditorProvider.TIPTAP;

    public EditorProvider getProvider() {
        return provider;
    }

    public void setProvider(EditorProvider provider) {
        this.provider = provider;
    }
}
