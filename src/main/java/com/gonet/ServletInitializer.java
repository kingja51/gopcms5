package com.gonet;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/** 외부 Tomcat 10.1.x 배포 진입점 — 표준 war (repackage 미사용, pom.xml 주석 참조). */
public class ServletInitializer extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(GopcmsApplication.class);
    }
}
