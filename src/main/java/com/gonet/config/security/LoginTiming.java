package com.gonet.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 계정 존재 여부가 <b>응답 시간으로 새어 나가지 않게</b> 한다.
 *
 * <p>없는 계정은 비밀번호를 비교할 대상이 없어 즉시 실패한다. 반면 있는 계정은 BCrypt 를
 * 한 번 돌리므로 수십~수백 ms 가 더 걸린다. 문구를 똑같이 맞춰도 <b>시간 차이만으로</b>
 * "이 아이디는 존재한다" 를 알 수 있고, 그렇게 모은 목록이 크리덴셜 스터핑의 입력이 된다.
 *
 * <p>그래서 없는 계정에도 더미 해시로 BCrypt 를 한 번 돌린다. 계산을 버리는 셈이지만,
 * 그 계산이 곧 방어다.
 */
@Component
@RequiredArgsConstructor
public class LoginTiming {

    /**
     * 비교용 더미 해시 — 어떤 입력과도 일치하지 않는다.
     *
     * <p>실제 계정의 해시를 쓰면 그 계정의 비밀번호를 맞혔을 때 통과해 버린다.
     * 무작위 값으로 만든 해시라 매칭될 일이 없다.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PasswordEncoder passwordEncoder;

    /**
     * 존재하지 않는 계정을 처리할 때 호출한다 — 있는 계정과 같은 시간을 쓴다.
     *
     * <p>결과는 쓰지 않는다. 호출 자체가 목적이다.
     */
    public void burn(String rawPassword) {
        passwordEncoder.matches(rawPassword == null ? "" : rawPassword, DUMMY_HASH);
    }
}
