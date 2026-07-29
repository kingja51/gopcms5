# GoPCMS 회원(Member) 기술 명세서

> **문서 목적** 회원(Member) 도메인의 가입·인증·정보관리·휴면·탈퇴·관리자 운영까지의 모든 조건·기능·정책을 단일 문서로 통합.
> **기준 시점** 2026-04-23 ( 관리자/Admin 과 별개 — 본 문서는 `ROLE_MEMBER` 도메인 한정)
> **대상 독자** 신규 합류 개발자 / 보안 검토자 / 운영자 / QA
> **관련 소스** `com.gonet.primary.member.**`, 템플릿 `src/main/resources/templates/front/**`·`admin/system/member/**`
> **선결 참고** [CLAUDE.md](../CLAUDE.md) · [GoPCMS_테이블설계서_MariaDB11.7.md](GoPCMS_테이블설계서_MariaDB11.7.md) · `docs/ddl-requests/`

---

## 0. 요약 (Executive Summary)

| 구분 | 내용 |
|---|---|
| 로그인 경로 | `/member/login` (관리자 `/adm/login` 과 물리 분리, `AdminUserDetailsService` / `MemberUserDetailsService` 우회 차단) |
| 가입 경로 | `/member/join` 7-step 플로우 (유형선택 → 약관 → CI본인인증 → 폼 → 가입 → 완료) |
| 가입 유형 | `ADULT` (만 14세 이상 본인인증) / `CHILD` (14세 미만, 법정대리인 CI 공유) |
| PII 보호 | email/phone/birthDate/ci/parentCi/address/addressDetail AES-256-GCM + HMAC-SHA256 중복확인 해시 (member_name 은 UNIQUE 인덱스 위해 평문) |
| 비밀번호 정책 | 8자+3종 OR 10자+2종 / BCrypt(12) / 최근 5건 재사용 금지 / 180일 만료 |
| 상태 | `ACTIVE` / `LOCKED` / `EMAIL_PENDING` / `SUSPENDED` (+ 휴면 `tb_member_dormant` / 탈퇴 `tb_member_withdraw`) |
| 휴면 라이프사이클 | 마지막 로그인 기준 335 / 358 / 364일 알림 → 365일 초과 시 자동 전환, 복원 플로우 3요소(이름·이메일·비밀번호) |
| 마이페이지 | 재인증(step-up) TTL 5분, 세션당 5회 실패 시 재인증 흔적 파기 |
| 관리자 권한 | 상태 변경 / 비밀번호 초기화(임시 PW 메일) / 잠금 해제 / 강제 탈퇴 / 엑셀 다운로드 (관리자는 회원 생성 불가) |
| 감사 | 모든 CUD 는 `AuditLogger.write(AuditEvent...)` 5-경로 (DB/Logback JSON/Event/server_audit/binlog) |

---

## 1. 도메인 범위와 제약

### 1.1 스코프
회원(일반 사용자) 도메인에 한정. ·관리자(`Admin`)·관리자 그룹(`AdminGroup`) 도메인은 본 문서 범위 외 — 패키지·테이블·로그인 엔드포인트 모두 분리되어 있다.

### 1.2 관련 테이블 (gopcms_primary)
| 테이블 | 용도 | PK |
|---|---|---|
| `tb_member` | 활성 회원 (상태=ACTIVE/LOCKED/EMAIL_PENDING/SUSPENDED) | `member_id` VARCHAR(36) UUIDv7 |
| `tb_member_dormant` | 1년 초과 미로그인 이관분 (로그인 차단) | `member_id` 공유 (삭제-삽입 이관) |
| `tb_member_withdraw` | 탈퇴 이력(보관기한 5년) | `member_id` |
| `tb_member_consent` | 약관 동의 이력 (5종 × 버전) | `member_consent_id` |
| `tb_member_password_history` | 비밀번호 재사용 금지 검증 (최근 5건 BCrypt 해시) | `pwd_history_id` |
| `tb_member_dormant_notice` | 휴면 알림 중복 발송 방지 `(member_id, stage)` UNIQUE | `notice_id` |
| `v_user_login` | 회원+관리자 통합 로그인 VIEW (휴면·탈퇴 자동 제외) | VIEW |

### 1.3 고정 결정
- **가입은 본인만** — 관리자는 회원을 생성할 수 없다(정책).
- **`tb_member_role` 미사용** — 회원은 로그인 시 `ROLE_MEMBER` 가 암묵 부여된다. URL 가드는 `ROLE` 이 아니라 `AUTHENTICATED + user_type=MEMBER` 로 한다.
- **`member_name` 은 평문 VARCHAR(150)** — UNIQUE 인덱스(`uk_member_identity`) 에 포함되기 때문. 2026-04-23 DDL 로 `{AG}` 해제 완료.
- **DI(중복가입확인정보) 컬럼 제거** — 본 서비스는 CI 만으로 충분, 2026-04-23 DDL 로 DROP.

---

## 2. 데이터 모델

### 2.1 Member 엔티티 ([Member.java](../src/main/java/com/gonet/primary/member/dto/Member.java))
```
memberId           VARCHAR(36) PK
memberSeq          BIGINT AUTO_INCREMENT (정렬·성능용)
siteId             VARCHAR(36) FK tb_site
loginId            VARCHAR(50)  소문자 시작 + a-z0-9_ + 8~50자
password           VARCHAR(72)  BCrypt(12)
passwordChangedAt / passwordExpireAt
roleIds            CSV (계층 확장) — 회원은 항상 "ROLE_MEMBER" 만
groupIds           CSV (등급·혜택)

memberName         VARCHAR(150) 평문 — UNIQUE 인덱스 구성
nickname           VARCHAR(100) 평문
email              {AG}         HMAC emailHash 동시 저장
phone              {AG}         HMAC phoneHash 동시 저장
birthDate          {AG}         YYYYMMDD
gender             CHAR(1)      M/F/N

ci                 {AG}         본인/부모 CI (CHILD 시 부모 CI 공유)
ciHash             CHAR(64)     HMAC-SHA256 중복확인

parentName         VARCHAR(150) 평문 — CHILD 만
parentCi           {AG}         CHILD 만
parentCiHash       CHAR(64)     CHILD 만

addressZipcode / address / addressDetail  (address/addressDetail = {AG})

joinType           EMAIL | KAKAO | NAVER | ... (SSO 는 미구현)
status             ACTIVE / LOCKED / EMAIL_PENDING / SUSPENDED
loginFailCount / lockedUntil
lastLoginAt / lastLoginIp / lastAccessAt / dormantScheduledAt

privacyAgreeYn / termsAgreeYn / marketingAgreeYn / smsAgreeYn / emailAgreeYn
deleteYn (soft delete 표식)

6 감사컬럼 (BaseEntity) — createdBy/Ip/At + updatedBy/Ip/At 자동 주입
```

### 2.2 UNIQUE 제약 (중복 가입 차단)
`uk_member_identity (site_id, member_name, ci_hash, parent_ci_hash)`
- **성인**: `parent_ci_hash = NULL` — MariaDB NULL 은 UNIQUE 검사 제외 → 이름·CI 조합으로 중복 차단
- **14세 미만**: 같은 부모+같은 이름 자녀의 중복 가입 차단, 다른 부모/다른 이름은 통과

### 2.3 암호화 정책
| 필드 | 저장 형태 | 검색 경로 |
|---|---|---|
| email | `{AG}<base64(iv‖ct‖tag)>` | `email_hash` HMAC-SHA256 (소문자 정규화) |
| phone | `{AG}...` | `phone_hash` HMAC-SHA256 |
| ci / parentCi | `{AG}...` | `ci_hash` / `parent_ci_hash` HMAC-SHA256 |
| birthDate / address / addressDetail | `{AG}...` | 검색 불가 (전수 복호화 필요 시 성능 이슈) |

AES-GCM 은 [EncryptInterceptor](../src/main/java/com/gonet/common/crypto/EncryptInterceptor.java) 가 `@Encrypt` 필드 감지 → 자동 암복호. HMAC 은 [EmailHasher](../src/main/java/com/gonet/common/crypto/EmailHasher.java) 를 서비스 계층에서 직접 호출해 세팅.

---

## 3. 회원 가입 플로우 ([MemberJoinUsrController](../src/main/java/com/gonet/primary/member/controller/MemberJoinUsrController.java))

### 3.1 단계 개요 (비로그인 접근)
```
Step 1  GET  /member/join                         → 유형 선택 (ADULT / CHILD)
Step 2  GET  /member/join/agree?userType=...      → 약관 4종 동의
        POST /member/join/agree                   → agreed=true 세션 기록
Step 3  GET  /member/join/ci?userType=...         → CI 본인인증 페이지 (mock)
Step 4  POST /member/join/ci/callback             → CI 결과 세션 저장 + redirect step5
Step 5  GET  /member/join/form                    → 가입 폼 (세션값 prefill)
Step 6  POST /member/join                         → 가입 실행 (세션 CI 소비)
Step 7  GET  /member/join/complete?memberId=...   → 완료 안내
```

### 3.2 세션 관리 — `JoinSessionData` (SESS_KEY)
| 필드 | 용도 |
|---|---|
| `userType` | ADULT / CHILD — 서버가 세션값만 신뢰 (폼 hidden 은 조작 가능) |
| `agreed` | 약관 동의 플래그 |
| `ciVerified` / `verifiedCi` / `verifiedCiHash` / `verifiedName` / `verifiedPhone` | CI 인증 결과 |

Step 6 완료 시 `session.removeAttribute(SESS_KEY)` — 반쯤 진행된 플로우 재진입 차단.

### 3.3 CI 본인인증 (mock ↔ 실연동)
- 현재 구현: `MemberJoinUsrController.generateMockCi()` — SecureRandom 32byte hex, prefix `MOCK_`
- 실서비스 전환: NICE/KCB 팝업 callback 으로 Step 4 대체. 응답 CI 를 `JoinSessionData.verifiedCi` 에 그대로 주입.

### 3.4 ADULT vs CHILD 세부 규칙
| 항목 | ADULT | CHILD (14세 미만) |
|---|---|---|
| CI 주체 | 본인 | 법정대리인(부모) |
| `ci / ci_hash` | 본인 CI | 부모 CI 를 자녀 ci 에도 복사 |
| `parent_name` | NULL | 필수 — `@AssertTrue isParentNameRequiredWhenChild` |
| `parent_ci / parent_ci_hash` | NULL | 부모 CI |
| `member_name` 기본값 | 인증된 이름 prefill | 자녀 이름 (사용자 직접 입력) |
| `phone` 기본값 | 인증된 휴대폰 prefill | 부모 휴대폰 prefill |

### 3.5 가입 폼 ([MemberJoinForm](../src/main/java/com/gonet/primary/member/dto/MemberJoinForm.java)) 검증
| 필드 | 제약 |
|---|---|
| `userType` | `^(ADULT|CHILD)$` — 서버는 세션값으로 강제 덮어쓰기 |
| `loginId` | 8~50자 + `^[a-z][a-z0-9_]+$` |
| `password` | `@PasswordPolicy` — 8자+3종 또는 10자+2종 |
| `passwordConfirm` | `@AssertTrue isPasswordMatched` |
| `memberName` | NotBlank, max 100 |
| `email` | `@Email`, max 255 |
| `phone` | `\\d{2,3}-?\\d{3,4}-?\\d{4}` 선택 |
| `birthDate` | `\\d{8}` 선택 (YYYYMMDD) |
| `gender` | `[MFN]` 선택 |
| `termsAgreeYn`, `privacyAgreeYn` | `Y` 필수 |
| `marketingAgreeYn`, `smsAgreeYn`, `emailAgreeYn` | 선택, 기본 `N` |
| `parentName` | CHILD 필수 |

### 3.6 중복 검사
| 검사 | 시점 | 키 |
|---|---|---|
| loginId | Step 5 htmx 실시간 + Step 6 서버 | `existsByLoginId(siteId, loginId)` |
| email | Step 6 | `existsByEmailHash(emailHash)` |
| identity | Step 6 — INSERT 시 `DuplicateKeyException` | `uk_member_identity` |

실시간 ID 중복확인: `GET /member/join/check-login-id?loginId=...` → `front/fragments/check-login-id :: result` (AVAILABLE/TAKEN/INVALID).

### 3.7 서비스 `MemberServiceImpl.join()` 핵심
1. CI 세션 검증 — `isCiVerified() && verifiedCi != null`, 없으면 처음부터
2. loginId / email_hash 중복 검사
3. `Member` 엔티티 구성 — BCrypt 비번, 180일 만료 세팅
4. CHILD 분기 — `parent_name`, `parent_ci = ci`, `parent_ci_hash = ci_hash`
5. `mapper.insert(m)` — DuplicateKeyException → "이미 가입된 정보" 변환
6. `insertPasswordHistory` — 재사용 금지 이력 1건
7. `MemberConsent` 5건 INSERT (TERMS/PRIVACY/MARKETING/SMS/EMAIL) + version=1.0
8. `MEMBER_WELCOME` 템플릿 메일 비동기 전송 (실패해도 가입 유지)

### 3.8 현재 미구현
- `EMAIL_PENDING` 상태를 거친 이메일 인증 토큰 발급 — **본 1차 구현은 즉시 `ACTIVE`**. (S5 후속)
- SSO (카카오/네이버) — `joinType` 만 확장 지점으로 예약.

---

## 4. 회원 로그인

### 4.1 엔드포인트 & Security 체인
- 폼: [front/member-login.html](../src/main/resources/templates/front/member-login.html)
- POST: `/member/login` — Spring Security 체인 `@Order 20` ([SecurityConfig](../src/main/java/com/gonet/config/security/SecurityConfig.java))
- UserDetailsService: [MemberUserDetailsService](../src/main/java/com/gonet/primary/system/login/service/MemberUserDetailsService.java) (MEMBER 만)
- 로그아웃: `/member/logout` → `/member/login?logout=1`
- 성공: `/member/mypage`
- 실패: `/member/login?error=<CODE>` — URL 에는 enumeration 차단을 위해 fail count/locked 표시하지 않고, **세션 속성**(`LoginFeedback`) 으로만 한 번 소비

### 4.2 형식 필터 + Rate Limit (CLAUDE.md §0.3)
- [LoginFormatValidationFilter](../src/main/java/com/gonet/config/security/LoginFormatValidationFilter.java) 가 `/member/login` POST 에서 loginId 8자·password 정책 Bean Validation 선적용. 위반 → `?error=INVALID_FORMAT`
- [RateLimitFilter](../src/main/java/com/gonet/config/security/RateLimitFilter.java) IP 버킷(10/min) **AND** loginId 버킷(5/min) 이중 키 — 분산 IP 공격 방어

### 4.3 실패 카운트 & 잠금
- `MemberMapper.incrementLoginFailAndMaybeLock(loginId, threshold, lockMinutes)` — 원자적 증가 + threshold 도달 시 `locked_until` 자동 설정
- 기본 threshold=5, lockMinutes=30 (값은 설정 기반, 본 문서는 구현 참조)
- 사용자에게 보여주는 실패 횟수/잠금 해제 시각은 `LoginFeedback` 1회 소비

### 4.4 세션 쿠키 (CLAUDE.md §0.3)
- 이름 **`PCMS_SID`** (JSESSIONID 에서 변경 — 배포 직후 전원 재로그인)
- HttpOnly / Secure / SameSite=Lax
- `sessionFixation().changeSessionId()` 명시

### 4.5 통합 로그인 VIEW `v_user_login`
- 회원(`tb_member`) + 관리자(`tb_admin`) UNION — **휴면(`tb_member_dormant`) · 탈퇴(`tb_member_withdraw`) 는 자동 제외**
- 컬럼: `user_seq, user_type(MEMBER/ADMIN), login_id, password, role_ids, group_ids, ...`
- 로그인 성공 후 `updateLastLogin(memberId, at, ip)` + 휴면 알림 이력 전체 삭제(재사이클)

---

## 5. 마이페이지 ([MyPageUsrController](../src/main/java/com/gonet/primary/member/controller/MyPageUsrController.java))

### 5.1 엔드포인트
| 경로 | 메서드 | 재인증 | 설명 |
|---|---|---|---|
| `/member/mypage` | GET | 불필요 | 대시보드(요약 + 동의 이력) |
| `/member/mypage/verify` | GET/POST | — | step-up 재인증 폼 (이름+이메일+비밀번호) |
| `/member/mypage/profile` | GET/POST | **필수** | 개인정보 수정 |
| `/member/mypage/password` | GET/POST | 폼 내부 현재비번 검증으로 대체 | 비밀번호 변경 |
| `/member/mypage/withdraw` | GET/POST | **필수** | 본인 셀프 탈퇴 |

### 5.2 재인증(step-up) — [MyPageReauth](../src/main/java/com/gonet/primary/member/service/MyPageReauth.java)
- **TTL 5분** (`MyPageReauth.TTL`) — 성공 시 `mark()` → 민감 페이지 자유 왕래
- 세션 속성 `PCMS_MEMBER_REAUTH_AT`, 실패 카운트 `PCMS_MEMBER_REAUTH_FAIL`
- 실패 허용 **5회**(`FAIL_LIMIT`) 초과 시 `clearAll()` → 재입력 강제(로그인 자체는 유지)
- 검증 3요소: `memberName + email + password` — 서비스 `verifyReauth()` 는 **타이밍 공격 완화 더미 해시** 로 BCrypt 를 항상 1회 수행

### 5.3 개인정보 수정 `updateProfile()`
- 변경 가능: memberName / nickname / email / phone / zipcode / address / addressDetail / marketing·sms·emailAgreeYn
- **변경 불가**: loginId / birthDate / gender / ci / parent* — 본인인증 근간값은 수정 불가
- email 중복확인: `existsByEmailHash(emailHash, memberId)` (자기 제외)
- 선택동의 3종 변경 시 `MemberConsent` 이력 재기록 (IP/UA 와 함께)

### 5.4 비밀번호 변경 `changePassword()`
1. `currentPassword` BCrypt 검증
2. `newPassword == currentPassword` 금지
3. 최근 5건(`PASSWORD_HISTORY_CHECK`) 이력과 BCrypt 비교 — 재사용 금지
4. `updatePassword(...)` + 만료일 now+180일 + `insertPasswordHistory(...)`
5. `PASSWORD_CHANGED` 템플릿으로 보안 알림 메일 (발신자 탈취 탐지용, 실패해도 변경 유지)

### 5.5 셀프 탈퇴 `withdraw()` ([MemberWithdrawForm](../src/main/java/com/gonet/primary/member/dto/MemberWithdrawForm.java))
- 재인증 필수 + 탈퇴사유 10자 이상
- `WithdrawReasons.normalize(reason, WITHDRAW_CATEGORIES, "USER_REQUEST")` — 자유 텍스트는 `log_audit.after_value.note` 로만 분리 저장(PII 최소화)
- `tb_member_withdraw` INSERT (retention=now+5년) + `softDelete(memberId)` (`delete_yn='Y'`)
- 세션 `invalidate()` + `SecurityContextHolder.clearContext()` → `/member/login?withdraw=1`
- `MEMBER_WITHDRAW` 템플릿 메일 전송

### 5.6 마이페이지 민감 페이지 보호 매트릭스
| 시나리오 | 동작 |
|---|---|
| 로그인만 한 상태로 /profile 진입 | `/member/mypage/verify?next=profile` 로 리다이렉트 |
| 재인증 성공 후 5분 이내 | `/profile`·`/withdraw` 자유 왕래 |
| 5분 경과 | 재인증 만료, verify 화면 이동 |
| verify 5회 실패 | `clearAll()` + `"횟수 초과" flash` + `/member/mypage` |

---

## 6. 아이디·비밀번호 찾기 ([MemberFindUsrController](../src/main/java/com/gonet/primary/member/controller/MemberFindUsrController.java))

### 6.1 아이디 찾기
- 경로: `/member/find-id`
- 입력: email 단독
- 서비스: `findByEmail(siteId, email)` → `email_hash` HMAC 매칭
- 결과 화면: **마스킹된 loginId** (`@mask.loginId`) + 가입일 — 전체 ID 노출 금지

### 6.2 비밀번호 찾기 (임시 비밀번호 메일)
- 경로: `/member/find-password`
- 입력: loginId + email
- 서비스: `selfResetPassword(siteId, loginId, email)` → `findByLoginIdAndEmailHash()` 성공 + `status=ACTIVE` 조건
- 내부적으로 `resetPasswordAndSendMail(memberId)` 호출
  1. `RandomPasswordGenerator.generate()` — 임시 비밀번호 (**로그·예외 메시지 평문 금지**)
  2. BCrypt 인코딩 → DB 저장 + `passwordExpireAt = now` (즉시 만료)
  3. 로그인 후 변경 강제 흐름 진입
  4. `PASSWORD_RESET` 템플릿 메일 — 실패 시 트랜잭션 롤백 (IllegalStateException)

### 6.3 Enumeration 방지
- 미일치 / 비활성 / 메일실패 모두 동일 generic 메시지 — `"입력하신 정보와 일치하는 회원을 찾을 수 없습니다."` 또는 `"처리 중 오류가 발생했습니다."`
- BCrypt 는 미존재 계정에도 더미 해시로 항상 1회 수행 (타이밍 균일화 — `MemberServiceImpl.verifyReauth` 참조)

---

## 7. 상태 전이 모델

```
                    ┌────────────┐
                    │   (신규)   │
                    └─────┬──────┘
                         가입(즉시 ACTIVE)
                           ▼
     ┌──────────────── ACTIVE ──────────────────┐
     │                   │                     │
     │  연속 로그인 실패   │   관리자 강제        │
     │  5회               │   SUSPENDED        │
     ▼                    ▼                     ▼
  LOCKED (30m)      EMAIL_PENDING        SUSPENDED
   │ 잠금 만료       (이메일 인증 대기)     (사용 불가)
   │  or 관리자     (S5 도입)
   │  unlock
   ▼
 ACTIVE
     │
     │  마지막 로그인 365일 초과
     ▼
 휴면 (tb_member_dormant)   ← 로그인 차단. 복원 플로우로 복귀
     │ 복원
     ▼
 ACTIVE
     │
     │  셀프 탈퇴 / 관리자 강제 / 휴면기간 만료
     ▼
 탈퇴 (tb_member_withdraw + delete_yn='Y')  ← 5년 보관 후 파기
```

### 7.1 상태 enum
`MemberServiceImpl.adminUpdateStatus()` 허용 집합: `ACTIVE`, `LOCKED`, `EMAIL_PENDING`, `SUSPENDED` — 그 외 값은 `IllegalArgumentException`.

---

## 8. 휴면 계정 라이프사이클

### 8.1 스케줄 (DormantScheduler)
- `@Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")` — **매일 01:00 KST**
- 단일 인스턴스 전제 (다중 배포 시 ShedLock 등 분산 잠금 필요)

### 8.2 단계별 cutoff — [DormantServiceImpl](../src/main/java/com/gonet/primary/member/dormant/service/DormantServiceImpl.java)
| 단계 | cutoff 일수 | 의미 | 템플릿 |
|---|---|---|---|
| 30D 알림 | 335 | 전환 30일 전 | `ACCOUNT_DORMANT_NOTICE_30D` |
| 7D 알림 | 358 | 전환 7일 전 | `ACCOUNT_DORMANT_NOTICE_7D` |
| 1D 알림 | 364 | 전환 1일 전 | `ACCOUNT_DORMANT_NOTICE_1D` |
| 전환 | 365 | tb_member → tb_member_dormant 이관 | `ACCOUNT_DORMANT_TRANSFERRED` |
| 복원 알림 | — | 본인 확인 후 역이관 성공 | `ACCOUNT_DORMANT_RESTORED` |

### 8.3 중복 발송 방지
- `tb_member_dormant_notice (member_id, stage) UNIQUE` — 동일 단계 메일 2회 전송 방지
- 로그인 성공 시 해당 회원의 알림 이력 전체 DELETE → 다음 사이클 리셋
- 휴면 전환 시 FK CASCADE 로 이력 자동 정리

### 8.4 독립 트랜잭션 패턴
- 각 단건은 [DormantBatchWorker](../src/main/java/com/gonet/primary/member/dormant/service/DormantBatchWorker.java) `REQUIRES_NEW` — **1건 실패해도 다음 진행**
- self-invocation AOP 우회 방지를 위해 별도 빈으로 분리

### 8.5 복원 플로우 ([DormantRestoreUsrController](../src/main/java/com/gonet/primary/member/dormant/controller/DormantRestoreUsrController.java))
- 경로: `GET/POST /member/dormant/restore`
- 3요소 검증: loginId + memberName + email + password
  - 실패 시 enumeration 방지 — 어느 항목이 틀렸는지 구분 없음, 단일 메시지
  - 타이밍 균일화 — 미존재 계정에도 더미 해시 BCrypt compare 1회
- 성공 시: `tb_member_dormant` → `tb_member` 역이관 (status=ACTIVE) + `ACCOUNT_DORMANT_RESTORED` 메일 + `/member/login` 리다이렉트

### 8.6 보관 기한
- 휴면 이후 5년(`RETENTION_YEARS`) 은 안내 문구용. 실제 파기는 별도 배치 (현재 미구현 — S6 대상).

---

## 9. 약관 동의 (MemberConsent)

### 9.1 동의 유형 (5종 + 1)
| type | 필수 여부 | 화면 |
|---|---|---|
| `TERMS` | 가입 필수 | join-agree |
| `PRIVACY` | 가입 필수 | join-agree |
| `MARKETING` | 선택 | join-agree / mypage-profile |
| `SMS` | 선택 | join-agree / mypage-profile |
| `EMAIL` | 선택 | join-agree / mypage-profile |
| `THIRD_PARTY` | 예약(미사용) | — |

### 9.2 버전 관리
- `CONSENT_VERSION = "1.0"` 상수 — 약관 문구 변경 시 bump
- 가입 시점/수정 시점 모두 클라이언트 IP·UA 와 함께 `tb_member_consent` 에 INSERT (UPDATE 아님 — 이력 누적)

### 9.3 재동의 트리거
- 개인정보 수정 시 선택동의 3종(MARKETING/SMS/EMAIL) 은 현재 체크 상태를 그대로 재기록

---

## 10. 관리자 운영 기능 ([MemberMngController](../src/main/java/com/gonet/primary/member/controller/MemberMngController.java))

### 10.1 엔드포인트 `/admin/system/member`
| 메서드 | 경로 | 기능 | 감사 Action |
|---|---|---|---|
| GET | `/` | 목록 (페이징·검색) | — |
| GET | `/{memberId}` | 상세 (동의이력 포함) | — |
| POST | `/{memberId}/status` | 상태 변경 | `MEMBER_STATUS_CHANGE` |
| POST | `/{memberId}/reset-password` | 임시 비밀번호 메일 | `MEMBER_PWD_RESET` |
| POST | `/{memberId}/unlock` | 잠금 해제 | `MEMBER_UNLOCK` |
| DELETE | `/{memberId}` | 강제 탈퇴 | `MEMBER_FORCE_DELETE` |
| POST | `/excel` | 엑셀 다운로드 (사유필수) | `EXCEL_DOWNLOAD` |

### 10.2 검색 조건 ([MemberSearch](../src/main/java/com/gonet/primary/member/dto/MemberSearch.java))
- PageRequest 상속 — page / pageSize / keyword / sortBy / sortDir
- `keyword`: 평문 검색 가능 컬럼(**login_id, nickname**) 만 대상. email/name 은 암호화 → LIKE 불가
- `emailExact`: HMAC 정확 매칭 경로
- `status`, `siteId`, `joinType` 다중 필터

### 10.3 상태 변경 허용 값
`ACTIVE | LOCKED | EMAIL_PENDING | SUSPENDED` — 그 외는 거부 (`IllegalArgumentException`)

### 10.4 비밀번호 초기화 흐름
1. 임시 비밀번호 생성 (`RandomPasswordGenerator`) — 평문은 로컬 변수 내 존재만 허용, **로그/예외 평문 노출 절대 금지**
2. BCrypt 인코딩 + `updatePassword(memberId, encoded, now, now)` — 즉시 만료 → 다음 로그인에서 변경 강제
3. `PASSWORD_RESET` 템플릿 메일 — 실패 시 트랜잭션 롤백 (사용자에게 "잠시 후 재시도" 안내)

### 10.5 잠금 해제
`unlockMember(memberId)` — `locked_until=NULL, login_fail_count=0`. 0 행 영향 시 `IllegalArgumentException`.

### 10.6 강제 탈퇴 (`adminSoftDelete`)
- 카테고리 기본 `ADMIN_FORCE` — 자유 텍스트는 `log_audit.after_value` 로만 분리
- `tb_member_withdraw` INSERT (retention=now+5년) + `softDelete(memberId)`
- 회원 생성은 관리자에게 부여하지 **않음**

### 10.7 엑셀 다운로드 규약 (CLAUDE.md §3.6)
- `POST /admin/system/member/excel` — GET 금지
- `@Valid ExcelDownloadRequest` — `downloadReason` 10자 이상 필수
- 컬럼: 회원ID / 사이트 / 로그인ID / **이름(마스킹)** / **이메일(마스킹)** / **전화(마스킹)** / 상태 / 가입유형 / 최종로그인 / 등록일시
- 감사: `EXCEL_DOWNLOAD`, after JSON `{"count":N,"reason":"..."}`
- 파일명 prefix: `gopcms_members`
- 페이지 크기 상한: 10,000 (대량 다운로드 차단)

---

## 11. 감사 로깅

모든 CUD 는 [AuditLogger.write(AuditEvent...)](../src/main/java/com/gonet/common/audit/AuditLogger.java) 단일 진입점 → 5경로 기록 (log_audit DB / Logback JSON `com.gonet.audit` / Spring Event / DBA server_audit / binlog).

### 11.1 Action 분류표
| Action | Entity | 발생 시점 |
|---|---|---|
| `MEMBER_JOIN` (현재 로그만, AuditEvent 미사용 — 잠재 보강 포인트) | `tb_member` | 회원 가입 성공 |
| `MEMBER_PROFILE_UPDATE` (로그) | `tb_member` | 마이페이지 수정 |
| `MEMBER_PWD_CHANGE` | `tb_member_password_history` | 비밀번호 변경 |
| `MEMBER_WITHDRAW` | `tb_member_withdraw` | 셀프 탈퇴 |
| `MEMBER_FORCE_DELETE` | `tb_member_withdraw` | 관리자 강제 탈퇴 |
| `MEMBER_STATUS_CHANGE` | `tb_member` | 관리자 상태 변경 |
| `MEMBER_PWD_RESET` | `tb_member` | 관리자 비밀번호 초기화 |
| `MEMBER_UNLOCK` | `tb_member` | 관리자 잠금 해제 |
| `EXCEL_DOWNLOAD` | `tb_member` | 엑셀 다운로드 |
| `DORMANT_NOTICE` (로그) | `tb_member_dormant_notice` | 알림 발송 |
| `DORMANT_TRANSFER` (로그) | `tb_member_dormant` | 휴면 전환 |
| `DORMANT_RESTORE` (로그) | `tb_member` | 휴면 복원 |

### 11.2 after_value JSON 정책
- PII 평문 최소화 — 자유 텍스트(탈퇴 note 등) 은 DB 컬럼 대신 `after_value.note` 로만 저장
- `JsonUtils.quote(...)` 로 이스케이프 필수

### 11.3 로깅 시 민감값 원칙
- 평문 **비밀번호 절대 금지** — 임시 비밀번호 생성 시 로컬 변수만, 예외 메시지에도 포함 금지
- 이메일 해시는 `emailHash=***` 로 로깅 (원본 대신)
- 개인정보 뷰 로그는 MaskUtils 마스킹 후 기록 권장

---

## 12. 보안 규약 통합 체크리스트

| 항목 | 구현 위치 | 비고 |
|---|---|---|
| BCrypt(12) | Spring Security PasswordEncoder | work factor=12 고정 |
| 로그인 실패 5회 잠금(30m) | `incrementLoginFailAndMaybeLock` SP 원자성 | threshold/min 설정화 |
| Rate Limit (IP 10/min + loginId 5/min) | RateLimitFilter Bucket4j | 분산 IP 공격 방어 |
| 로그인 형식 필터 | LoginFormatValidationFilter | INVALID_FORMAT 리다이렉트 |
| Enumeration 방지 | LoginFeedback 세션 1회 소비 + 타이밍 균일화 BCrypt 더미 | 실패 원인 URL 노출 금지 |
| 세션 고정 공격 | `.changeSessionId()` 3체인 모두 | |
| 쿠키 보안 | PCMS_SID / HttpOnly / Secure / SameSite=Lax | |
| 재인증 TTL | MyPageReauth 5분 / 5회 실패 파기 | |
| PII 암호화 | @Encrypt AES-256-GCM / {AG} | |
| PII 해시 검색 | emailHash/phoneHash/ciHash/parentCiHash HMAC-SHA256 | |
| PII 마스킹 | MaskUtils (name/email/phone/loginId) + Thymeleaf `@mask` | |
| CSRF | Spring Security 기본 활성 | |
| CSP / HSTS / XFO / COOP / COEP / CORP | SecurityConfig header | nonce 기반 CSP |
| HttpFirewall | Spring 기본 firewall 엄격 모드 | |

---

## 13. 메일 템플릿 (tb_mail_template)

| templateCode | 시점 | 변수 |
|---|---|---|
| `MEMBER_WELCOME` | 가입 완료 | memberName, loginId, siteName, sentAt, loginUrl |
| `PASSWORD_RESET` | 임시비번 발송 | loginId, memberName, tempPassword, sentAt |
| `PASSWORD_CHANGED` | 본인 비번 변경 | memberName, loginId, changedAt, clientIp, userAgent |
| `MEMBER_WITHDRAW` | 탈퇴 완료 | memberName, loginId, siteName, withdrawAt, reason, retentionExpireAt, rejoinUrl |
| `ACCOUNT_DORMANT_NOTICE_30D/7D/1D` | 휴면 단계별 알림 | memberName, loginId, lastLoginAt |
| `ACCOUNT_DORMANT_TRANSFERRED` | 휴면 전환 완료 | memberName, loginId, dormantAt, retentionUntil |
| `ACCOUNT_DORMANT_RESTORED` | 휴면 복원 완료 | memberName, loginId |

템플릿은 [MailService.sendFromTemplate(code, to, model)](../src/main/java/com/gonet/common/mail/MailService.java) 경유 — Thymeleaf 표현식 `{{var}}` 치환. Resilience4j `@Retry + @CircuitBreaker` instance=`mail`.

---

## 14. 템플릿(View) 구성

### 14.1 프런트(회원)
```
front/
  member-login.html          로그인
  member-join.html           Step 5 가입 폼
  join-type-select.html      Step 1
  join-agree.html            Step 2
  join-ci.html               Step 3 (mock CI)
  join-complete.html         Step 7
  find-id.html               아이디 찾기
  find-password.html         비밀번호 찾기
  mypage.html                대시보드
  mypage-reauth.html         재인증
  mypage-profile.html        개인정보 수정
  mypage-password.html       비밀번호 변경
  mypage-withdraw.html       셀프 탈퇴
  dormant-restore.html       휴면 복원
  fragments/check-login-id.html   htmx fragment
```

### 14.2 관리자
```
admin/system/member/
  list.html      목록 + 검색 + 엑셀 다운로드 폼(<details> 디스클로저)
  detail.html    상세 + 상태/비번초기화/잠금해제/강제탈퇴 액션 + 동의이력
```

---

## 15. URL 인가 (RBAC)

### 15.1 규칙 소스 `tb_role_url_access`
| URL 패턴 | 가드 타입 | 허용 | 비고 |
|---|---|---|---|
| `/member/login`, `/member/join/**`, `/member/find-id`, `/member/find-password`, `/member/dormant/**` | `PERMIT_ALL` | 비로그인 | — |
| `/member/mypage/**` | `AUTHENTICATED(user_type=MEMBER)` | 회원만 | ROLE 매칭 금지 (2026-04-21b 수정) |
| `/admin/system/member/**` | `ROLE` (CSV 매칭 ROLE_STAFF 이상) | 관리자 | 회원 생성 불가 |

### 15.2 왜 ROLE 이 아닌 AUTHENTICATED+user_type 인가
회원은 `tb_member_role` 매핑이 없고 `ROLE_MEMBER` 만 로그인 시 암묵 부여 — `role_ids` CSV 매칭은 영구 실패 ([project_gopcms_member_role_policy.md](../../../Users/kingja/.claude/projects/D--claude-gopcms500/memory/project_gopcms_member_role_policy.md) 참조).

---

## 16. 배포·운영 주의사항

### 16.1 2026-04-22 적용
- loginId 8자 정책 — 기존 4~7자 계정이 1건이라도 있으면 앱 배포 즉시 전원 로그인 불가. [2026-04-22_loginid_8char_policy.sql](ddl-requests/2026-04-22_loginid_8char_policy.sql) OPT-A/B/C 중 택일.
- 쿠키 이름 JSESSIONID → PCMS_SID — 배포 직후 전원 재로그인 공지 필수.

### 16.2 2026-04-23 적용
- [2026-04-23_member_dormant_lifecycle.sql](ddl-requests/2026-04-23_member_dormant_lifecycle.sql) — `tb_member_dormant_notice` 생성 + 메일템플릿 5종 INSERT
- [2026-04-23_member_child_join_and_drop_di.sql](ddl-requests/2026-04-23_member_child_join_and_drop_di.sql) — member_name 평문 전환 + parent_* 3컬럼 추가 + di DROP + `uk_member_identity` UNIQUE 추가
  - **운영 DB 반영 전 필수**: member_name 이 기존에 `{AG}` 암호문이라면 앱 계층에서 SELECT→복호화→UPDATE 후 ALTER. 신규 환경만 바로 ALTER 가능.

### 16.3 휴면 배치 단일 인스턴스 전제
멀티 노드 배포 시 [DormantScheduler](../src/main/java/com/gonet/primary/member/dormant/scheduler/DormantScheduler.java) 가 N회 실행됨 — ShedLock 등 분산 잠금 도입 필요.

### 16.4 mock CI 실연동 전환
`MemberJoinUsrController.generateMockCi()` 를 NICE/KCB SDK 응답 CI 로 교체. `JoinSessionData.verifiedCi/verifiedCiHash` 인터페이스는 동일 유지.

---

## 17. 알려진 미구현 / TODO

| ID | 내용 | 예정 |
|---|---|---|
| M-01 | `EMAIL_PENDING` 이메일 인증 토큰 발급·검증 | S5 |
| M-02 | SSO 가입(KAKAO/NAVER) — joinType 확장 지점 예약 | S5~ |
| M-03 | 휴면 이후 5년 자동 파기 배치 | S6 |
| M-04 | `AuditLogger.write(...)` 호출 보강 — 회원 가입/프로필수정은 현재 log 로만 기록 | 차기 |
| M-05 | 휴면 전환 시 `ACCOUNT_DORMANT_TRANSFERRED` 메일 발송 훅 — 워커 내부에 위치 확인 필요 | 차기 |
| M-06 | 관리자 회원 열람 마스킹 해제(감사 사유 기록) API | S7 |

---

## 18. 테스트 가이드

### 18.1 단위/통합 테스트
- `MemberServiceImplTest` — 가입/프로필/비번변경/탈퇴 경계 케이스
- `MyPageReauthTest` — TTL / FAIL_LIMIT 경계
- `DormantServiceImplTest` — 단계 전이 / 복원 / enumeration 방지 타이밍
- ArchUnit — Controller 접미사 규약, DAO 직접호출 금지

### 18.2 시나리오 테스트 (SCENARIO_TEST.md 기준)
- `SC-MEMBER-01` ADULT 가입 → 로그인 → 마이페이지 접근
- `SC-MEMBER-02` CHILD 가입 (부모 CI 공유, parent_name 필수)
- `SC-MEMBER-03` 아이디 찾기 / 비밀번호 찾기 (마스킹·임시비번 메일)
- `SC-MEMBER-04` 재인증 TTL 만료 후 민감 페이지 재접근 흐름
- `SC-MEMBER-05` 5회 연속 로그인 실패 → 잠금 → 관리자 unlock
- `SC-MEMBER-06` 휴면 30D/7D/1D 알림 → 전환 → 복원
- `SC-MEMBER-07` 셀프 탈퇴 + 재가입 시 UNIQUE 제약 동작

---

## 19. 참조 파일 인덱스

**Controller**
- [MemberJoinUsrController.java](../src/main/java/com/gonet/primary/member/controller/MemberJoinUsrController.java)
- [MemberFindUsrController.java](../src/main/java/com/gonet/primary/member/controller/MemberFindUsrController.java)
- [MyPageUsrController.java](../src/main/java/com/gonet/primary/member/controller/MyPageUsrController.java)
- [MemberMngController.java](../src/main/java/com/gonet/primary/member/controller/MemberMngController.java)
- [DormantRestoreUsrController.java](../src/main/java/com/gonet/primary/member/dormant/controller/DormantRestoreUsrController.java)

**Service**
- [MemberService.java](../src/main/java/com/gonet/primary/member/service/MemberService.java) / [MemberServiceImpl.java](../src/main/java/com/gonet/primary/member/service/MemberServiceImpl.java)
- [MyPageReauth.java](../src/main/java/com/gonet/primary/member/service/MyPageReauth.java)
- [DormantService.java](../src/main/java/com/gonet/primary/member/dormant/service/DormantService.java) / [DormantServiceImpl.java](../src/main/java/com/gonet/primary/member/dormant/service/DormantServiceImpl.java)
- [DormantBatchWorker.java](../src/main/java/com/gonet/primary/member/dormant/service/DormantBatchWorker.java)
- [DormantScheduler.java](../src/main/java/com/gonet/primary/member/dormant/scheduler/DormantScheduler.java)

**DTO / Form**
- [Member.java](../src/main/java/com/gonet/primary/member/dto/Member.java)
- [MemberSearch.java](../src/main/java/com/gonet/primary/member/dto/MemberSearch.java)
- [MemberJoinForm.java](../src/main/java/com/gonet/primary/member/dto/MemberJoinForm.java)
- [MemberProfileForm.java](../src/main/java/com/gonet/primary/member/dto/MemberProfileForm.java)
- [MemberPasswordForm.java](../src/main/java/com/gonet/primary/member/dto/MemberPasswordForm.java)
- [MemberReauthForm.java](../src/main/java/com/gonet/primary/member/dto/MemberReauthForm.java)
- [MemberWithdrawForm.java](../src/main/java/com/gonet/primary/member/dto/MemberWithdrawForm.java)
- [MemberFindIdForm.java](../src/main/java/com/gonet/primary/member/dto/MemberFindIdForm.java)
- [MemberFindPasswordForm.java](../src/main/java/com/gonet/primary/member/dto/MemberFindPasswordForm.java)
- [MemberConsent.java](../src/main/java/com/gonet/primary/member/dto/MemberConsent.java)
- [JoinSessionData.java](../src/main/java/com/gonet/primary/member/dto/JoinSessionData.java)
- [DormantCandidate.java](../src/main/java/com/gonet/primary/member/dormant/dto/DormantCandidate.java)
- [DormantRestoreForm.java](../src/main/java/com/gonet/primary/member/dormant/dto/DormantRestoreForm.java)

**Mapper**
- [MemberMapper.java](../src/main/java/com/gonet/primary/member/mapper/MemberMapper.java)
- [MemberConsentMapper.java](../src/main/java/com/gonet/primary/member/mapper/MemberConsentMapper.java)
- [DormantMapper.java](../src/main/java/com/gonet/primary/member/dormant/mapper/DormantMapper.java)

**DDL 요청서**
- [2026-04-21c_member_find_urls.sql](ddl-requests/2026-04-21c_member_find_urls.sql)
- [2026-04-21d_member_join_check_login_id.sql](ddl-requests/2026-04-21d_member_join_check_login_id.sql)
- [2026-04-21b_member_mypage_fix.sql](ddl-requests/2026-04-21b_member_mypage_fix.sql)
- [2026-04-22_loginid_8char_policy.sql](ddl-requests/2026-04-22_loginid_8char_policy.sql)
- [2026-04-23_member_dormant_lifecycle.sql](ddl-requests/2026-04-23_member_dormant_lifecycle.sql)
- [2026-04-23_member_child_join_and_drop_di.sql](ddl-requests/2026-04-23_member_child_join_and_drop_di.sql)

---

## 20. 변경 이력

| 날짜 | 변경 | 상세 |
|---|---|---|
| 2026-04-23 | 휴면 라이프사이클 + 14세 미만 가입 + DI DROP | 본 문서 초판 |
| 2026-04-22 | loginId 8자 + 쿠키 PCMS_SID + enumeration 차단 | CLAUDE.md §0.3 반영 |
| 2026-04-21 | 회원/관리자 로그인 폼 분리 | `/member/login` 도입 |

---

**문서 소유자** 백엔드 플랫폼 팀
**리뷰 주기** 스프린트 종료 시점 or 회원 정책 변경 시
