package com.gonet.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * {@code @Async} 실행기 — 메일 발송처럼 <b>느린 외부 호출</b>을 요청 스레드에서 떼어 낸다.
 *
 * <p>가상 스레드를 쓴다. 메일·HTTP 같은 작업은 대부분 대기 시간이라 플랫폼 스레드를
 * 붙잡고 있을 이유가 없고, 이 프로젝트는 이미 Loom 을 켜 두었다(application.yml).
 * 풀 크기를 정할 필요도 없어진다 — 잘못 잡은 풀은 발송이 밀리거나 메모리를 먹는다.
 *
 * <p>주의: {@code @Async} 메서드는 <b>호출측 트랜잭션·SecurityContext 를 물려받지
 * 않는다</b>. 필요한 값은 인자로 넘겨야 한다.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "applicationTaskExecutor")
    public Executor getAsyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
