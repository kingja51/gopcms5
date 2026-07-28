package com.gonet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gopcms5 부트스트랩 — 임베디드 실행 진입점 (IntelliJ / mvn spring-boot:run).
 *
 * <p>외부 Tomcat 배포는 {@link ServletInitializer} 가 담당 (이중 진입점 — pom.xml 주석 참조).
 */
@SpringBootApplication
public class GopcmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GopcmsApplication.class, args);
    }
}
