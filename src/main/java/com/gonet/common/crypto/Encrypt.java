package com.gonet.common.crypto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 개인정보 컬럼 표시 — conventions §6.
 *
 * <p>이 어노테이션 자체는 <b>문서</b>다. 실제 암복호화는 매퍼 XML 이 컬럼에
 * {@link PiiTypeHandler} 를 지정해 수행한다 — MyBatis 는 필드 어노테이션을 보지 않기 때문이다.
 * 그래도 DTO 에 붙여 두는 이유는, 어느 필드가 PII 인지 코드에서 바로 보이게 하고
 * 리뷰·감사 때 누락을 찾을 수 있게 하기 위해서다.
 *
 * <p>서비스 코드는 <b>평문만</b> 다룬다. {@code {AG}} 프리픽스가 붙고 떨어지는 것은
 * TypeHandler 안에서만 일어난다.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Encrypt {

    /** 이 컬럼의 검색용 해시 컬럼 이름 — 비워 두면 검색 대상이 아니라는 뜻. */
    String hashColumn() default "";
}
