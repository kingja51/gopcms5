package com.gonet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * gopcms5 부트스트랩 — 임베디드 실행 진입점 (IntelliJ / mvn spring-boot:run).
 *
 * <p>외부 Tomcat 배포는 {@link ServletInitializer} 가 담당 (이중 진입점 — pom.xml 주석 참조).
 */
@SpringBootApplication
// gopcms.* 설정 바인딩 클래스를 한 번에 등록 — 파일 엔진/도메인 정책이 여기에 걸린다
@ConfigurationPropertiesScan("com.gonet")
public class GopcmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(GopcmsApplication.class, args);
    }
}
