package com.gonet.common.service;

import org.egovframe.rte.fdl.cmmn.EgovAbstractServiceImpl;

/**
 * 전 서비스 공통 부모 — eGov 서비스 아키텍처 규칙의 간접 상속 지점.
 *
 * <p>호환성 가이드 p.6: 모든 @Service 클래스는 서비스별 인터페이스를 구현하고
 * {@code EgovAbstractServiceImpl} 을 직접·간접 확장해야 한다. 본 클래스를 상속하면
 * 규칙이 충족되며, 프로젝트 공통 서비스 로직(감사·예외 규약 등)의 확장 지점이 된다.
 *
 * <p>규약: {서비스}ServiceImpl extends AbstractCmsService implements {서비스}Service.
 * 트랜잭션은 클래스 레벨 {@code @Transactional(readOnly=true, transactionManager=…)} 기본,
 * 쓰기 메서드는 반드시 writable override (CLAUDE.md 트랜잭션 함정).
 */
public abstract class AbstractCmsService extends EgovAbstractServiceImpl {
}
