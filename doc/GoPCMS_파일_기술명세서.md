# GoPCMS 파일 업로드 / 다운로드 기술명세서

- 작성일: 2026-04-24
- **최종 개정: 2026-05-01** — **7단계 download_auth 정책** + **OWNER_PRIVACY 가드** + **ROLE_PRIVACY 보유 체크** + **공지글 ANONYMOUS 우선** + **option A `ensureGroup()` 패턴** + **VARCHAR(40) ID + 도메인 prefix** + **ClamAV 운영 토글 통일**
- 대상 스프린트: **S3 스프린트 2·3번** + **S5 정책 강화** (2026-05-01)
- 참조 문서: [GoPCMS_설계서_및_개발계획서.md](GoPCMS_설계서_및_개발계획서.md), [GoPCMS_테이블설계서_MariaDB11.7.md](GoPCMS_테이블설계서_MariaDB11.7.md), [GoPCMS_게시판_기술명세서.md](GoPCMS_게시판_기술명세서.md)

---

## 1. 개요

### 1.1 배경
- 관리자·게시판·콘텐츠·팝업·배너·회원 프로필 등 **다수 도메인** 공통 파일 업로드/다운로드
- 웹쉘 침해 대응 경험 → **6중 방어 스택** 1순위
- 휴대폰 대용량 사진/동영상 업로드 실 운영 상황 고려
- **개인정보 첨부 보호** — QNA 등 1:1 비공개 게시판은 OWNER_PRIVACY 정책으로 본인+개인정보관리자만 접근

### 1.2 설계 원칙

| 원칙 | 구현 |
|---|---|
| **재사용 가능한 공통 엔진** | `com.gonet.common.file` — DB 미의존 |
| **도메인 책임 분리** | `com.gonet.primary.file` — DB 기록·조회·삭제·권한·감사만 |
| **6중 방어 스택** | 확장자 → Tika → 재인코딩 → SHA-256 → 격리 저장소 → ClamAV 큐 |
| **엔진 레벨 인증 가드** | `FileUploadServiceImpl.pipeline()` 진입 직후 SecurityContext 검사 |
| **카테고리별 API** | 문서/이미지/동영상/범용 4종 — 확장자·MIME family 교차 검증 |
| **다운로드 권한 단일 출처** | `tb_file_group.download_auth` — `enforceDownloadAuth()` 단일 진입점 |
| **option A — 도메인 Service 가 그룹 사전 생성** | `FileService.ensureGroup(entityType, entityId, siteId, downloadAuth)` |
| **공지글 ANONYMOUS 우선** | 게시판 도메인 측에서 정책 결정 — 본 도메인은 정책값 그대로 평가만 |
| **6감사컬럼 + 감사 이벤트** | 모든 CUD 에 `FILE_UPLOAD/DOWNLOAD/DELETE` + `FILE_PRIVACY_DOWNLOAD` 5경로 |

### 1.3 완성 상태 (2026-05-01)

- ✅ 업로드 6중 방어 스택 (ClamAV 실연동 — INSTREAM TCP 직구현 + 비동기 스캔 + retry 스케줄러)
- ✅ 카테고리별 업로드 (`uploadDocument` / `uploadImage` / `uploadVideo` / `uploadAny`)
- ✅ 다운로드: 단일 / 그룹 ZIP / 썸네일 / 관리자 전용(상태 무관)
- ✅ 비동기 썸네일 (`@Async("thumbnailExecutor")`)
- ✅ 물리 삭제 배치 (매일 04:00, 6개월 경과분)
- ✅ 다운로드 이력 `log_file_download`
- ✅ 재사용 file-picker fragment (드래그앤드롭 + 다중)
- ✅ **7단계 download_auth 정책** (2026-05-01)
- ✅ **OWNER_PRIVACY + ROLE_PRIVACY 가드** (Step 6, 2026-05-01)
- ✅ **option A `ensureGroup()`** — popup/banner/board 모두 적용
- ✅ **VARCHAR(40) + prefix** (FIL_/FG0_) (2026-05-01)
- ✅ **ClamAV 운영 토글 통일** — `gopcms.file.clamav.enabled=false` 시 NoOp + PENDING 다운로드 허용
- ⬜ 공격벡터 자동 테스트 20종 — S3-4 별도 스프린트
- ⬜ EXIF 제거 / ZIP 재귀검사 / 시그니처 갱신 후 재스캔 / Prometheus 메트릭 노출

---

## 2. 아키텍처

### 2.1 패키지 구조

```
com.gonet.common.file/                          ← 도메인-독립 엔진
├── config/
│   ├── FileUploadProperties                      · gopcms.file.upload.* 바인딩
│   ├── ClamAvProperties                          · gopcms.file.clamav.* 바인딩
│   ├── ClamAvAsyncConfig                         · @ConditionalOnProperty(enabled=true)
│   └── ThumbnailAsyncConfig                      · 비동기 썸네일 스레드풀
├── dto/
│   ├── UploadCategory (enum)                     · DOC / IMAGE / VIDEO / ANY
│   ├── UploadCommit                              · 엔진→호출자 결과
│   └── ZipEntrySpec (record)                     · ZIP 다운로드 entry
├── security/ (6중 방어 스택)
│   ├── FileExtensionValidator                    · #1 확장자 화이트리스트
│   ├── TikaMimeDetector                          · #2 Tika 매직바이트
│   ├── ImageReencoder                            · #3 Thumbnailator 재인코딩
│   ├── ThumbnailGenerator                        · 비동기 썸네일 400px JPG
│   ├── Sha256Hasher                              · #4 SHA-256 FIM
│   ├── FileStorage                               · #5 격리/정식/썸네일 저장소
│   ├── VirusScanQueue (interface)                · #6 ClamAV 큐 추상화
│   ├── NoOpVirusScanQueue                        · @ConditionalOnMissingBean fallback
│   ├── ClamAvClient                              · INSTREAM TCP 직구현
│   ├── UploadValidationException                 · 업로드 차단 → 400
│   └── UnauthenticatedUploadException            · 비인증 → 401
└── service/
    ├── FileUploadService / Impl                  · 업로드 orchestration
    └── FileDownloadService / Impl                · Range/ETag/Content-Disposition

com.gonet.primary.file/                         ← CRUD + 권한 + 감사
├── config/
│   └── FilePurgeProperties                       · gopcms.file.purge.*
├── dto/
│   ├── FileItem                                  · tb_file
│   ├── FileGroup                                 · tb_file_group
│   ├── FileSearch                                · 검색 (PageRequest)
│   ├── FileEntityType (enum)                     · BBS/CNT/MBR/POPUP/BANNER/MAIL/ETC
│   ├── VirusScanStatus (enum)                    · PENDING/CLEAN/INFECTED/ERROR
│   ├── UploadRequest                             · 업로드 폼/REST 파라미터
│   └── UploadResult                              · 업로드 응답
├── mapper/
│   ├── FileMapper + .xml                         · tb_file CRUD/배치
│   └── FileGroupMapper + .xml                    · tb_file_group CRUD + cascade
├── security/
│   └── ClamAvVirusScanQueue                      · @ConditionalOnProperty(enabled=true)
├── service/
│   ├── FileService / Impl                        · 업로드/다운로드/CRUD/권한 평가
│   ├── AsyncThumbnailService                     · @Async 썸네일 생성
│   └── FilePurgeService                          · 6개월 경과 물리 삭제
└── controller/
    ├── FileApiController                         · REST /api/v1/file/**
    ├── FileDownApiController                     · REST /fileDown/**
    └── FileMngController                         · 관리자 /admin/system/file

com.gonet.scheduler/                            ← 통합 스케줄러 (2026-04-28)
├── FilePurgeScheduler                            · @Scheduled 매일 04:00
└── VirusScanRetryScheduler                       · @Scheduled 매 5~10분 PENDING 재스캔

com.gonet.logging.file/                         ← 다운로드 이력
├── dto/FileDownloadLog                          · log_file_download
├── mapper/FileDownloadLogMapper
└── service/FileDownloadLogger / Impl
```

### 2.2 의존 다이어그램

```
[브라우저 file-picker fragment] ──multipart──▶ POST /api/v1/file/upload
                                                 │
                                                 ▼
                              FileApiController → FileService.upload()
                                                          │
                                       ┌──────────────────┴──────────────────┐
                                       ▼                                     ▼
                            FileUploadService.uploadXxx()           FileServiceImpl.commit()
                                       │                                     │
                              [6중 방어 스택]                            INSERT tb_file
                                       │                                     │
                                       ▼                              [virusScanQueue.enqueue]
                              UploadCommit (path/hash/category)             │
                                                                            ▼
                                                                  [ClamAvVirusScanQueue
                                                                    or NoOpVirusScanQueue]
                                                                            │
                                                                  CLEAN / INFECTED / ERROR
                                                                  → tb_file.virus_scan_status

[브라우저 다운로드 클릭] ───GET /fileDown/{fileId} ──▶ FileDownApiController
                                                           │
                                                           ▼
                                                  FileServiceImpl.download()
                                                           │
                                          ┌────────────────┴────────────────┐
                                          ▼                                 ▼
                                  enforceDownloadAuth()                 isDownloadable()
                                          │                                 │
                                  (7단계 분기)                          ★ ERROR 만 차단
                                          │
                                  ─ ANONYMOUS                              200/206/304
                                  ─ ROLE_*  (closure 매치)
                                  ─ OWNER_PRIVACY ★
                                       ├ ROLE_PRIVACY UUID 보유
                                       └ BoardArticleService.findCreatedBy()
                                  ─ 그 외 → AccessDenied 403
```

---

## 3. 데이터 모델

### 3.1 `tb_file` — 개별 파일

```sql
CREATE TABLE tb_file (
  file_id          VARCHAR(40)  NOT NULL,                    -- FIL_<UUID v7>
  file_group_id    VARCHAR(40)  NOT NULL,                    -- FG0_<UUID> FK
  original_name    VARCHAR(500) NOT NULL,
  stored_name      VARCHAR(500) NOT NULL,                    -- 디스크 파일명 (해시 포함)
  stored_path      VARCHAR(1000) NOT NULL,                   -- 격리 저장소 상대경로
  thumbnail_path   VARCHAR(1000),                             -- 이미지만, 비동기 채움
  extension        VARCHAR(20)  NOT NULL,
  mime_detected    VARCHAR(100),                              -- Tika 검출 MIME
  mime_client      VARCHAR(100),                              -- 클라이언트 신고 MIME (감사용)
  size_bytes       BIGINT       NOT NULL,
  file_hash        VARCHAR(64)  NOT NULL,                    -- SHA-256 hex
  is_image_yn      CHAR(1)      NOT NULL DEFAULT 'N',
  reencoded_yn     CHAR(1)      NOT NULL DEFAULT 'N',
  virus_scan_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING/CLEAN/INFECTED/ERROR
  download_count   BIGINT       NOT NULL DEFAULT 0,
  sort_order       INT          NOT NULL DEFAULT 0,
  delete_yn        CHAR(1)      NOT NULL DEFAULT 'N',
  -- 6감사컬럼 ...
  PRIMARY KEY (file_id),
  KEY idx_file_group (file_group_id, sort_order),
  KEY idx_file_hash (file_hash),
  KEY idx_file_scan (virus_scan_status, updated_at)          -- retry 스케줄러용
);
```

### 3.2 `tb_file_group` — 첨부 그룹 (다운로드 권한의 단위)

```sql
CREATE TABLE tb_file_group (
  file_group_id   VARCHAR(40)  NOT NULL,                    -- FG0_<UUID>
  entity_type     VARCHAR(50)  NOT NULL,                    -- BBS/CNT/MBR/POPUP/BANNER/...
  entity_id       VARCHAR(40)  NOT NULL,                    -- 도메인 PK (ART_<UUID> 등)
  site_id         VARCHAR(40),
  download_auth   VARCHAR(20)  NOT NULL DEFAULT 'ROLE_MEMBER',
                                                            -- ★ 7단계 정책 (§5)
  delete_yn       CHAR(1)      NOT NULL DEFAULT 'N',
  -- 6감사컬럼 ...
  PRIMARY KEY (file_group_id),
  KEY idx_filegroup_entity (entity_type, entity_id),
  CONSTRAINT chk_file_group_download_auth CHECK (download_auth IN (
    'ANONYMOUS','ROLE_MEMBER','ROLE_EMPLOYEE','ROLE_STAFF',
    'OWNER_PRIVACY','ROLE_MANAGER','ROLE_ADMIN'))
);
```

### 3.3 `log_file_download` — 다운로드 이력

```sql
CREATE TABLE log_file_download (
  log_id          BIGINT       NOT NULL AUTO_INCREMENT,
  file_id         VARCHAR(40),
  file_group_id   VARCHAR(40),
  download_type   VARCHAR(20),                              -- SINGLE / GROUP / ADMIN
  actor_user_id   VARCHAR(40),                              -- MBR/EMP/ADM_<UUID>
  actor_user_type VARCHAR(20),
  client_ip       VARCHAR(50),
  user_agent      VARCHAR(500),
  bytes_sent      BIGINT,
  trace_id        VARCHAR(40),
  created_by      VARCHAR(40),
  created_ip      VARCHAR(50),
  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id)
);
```

⚠️ append-only — `updated_*` 3컬럼 의도적 제외 ([§0.21](../CLAUDE.md))

### 3.4 ID Prefix 정책 (2026-05-01)

| 테이블 | prefix | 예 |
|---|---|---|
| `tb_file` | `FIL` | `FIL_019d9b80-...` |
| `tb_file_group` | `FG0` | `FG0_019d9b80-...` (2자 도메인 0 패딩) |

호출: `UuidV7Generator.generate("FIL")`, `UuidV7Generator.generate("FG0")`. 자동 prefix 검증.

레거시 36자 데이터는 prefix 없이 영구 보존.

---

## 4. 6중 방어 스택

```
[1] 클라이언트 multipart 업로드
       │
       ▼
[2] Spring multipart 한도 (gopcms.file.upload.max-file-size 200MB / max-request-size 250MB)
       │
       ▼
[3] FileUploadServiceImpl.pipeline()
       ├─ #1. FileExtensionValidator       (확장자 화이트리스트, null byte / path / 더블 ext 차단)
       ├─ #2. TikaMimeDetector              (매직바이트 검출, 정책과 family 교차 검증)
       ├─ #3. ImageReencoder                (이미지면 Thumbnailator 재인코딩 — EXIF/스크립트 제거)
       ├─ #4. Sha256Hasher                  (FIM 해시 계산)
       ├─ #5. FileStorage                   (격리 디렉토리 → 정식 디렉토리 이동)
       └─ #6. virusScanQueue.enqueue()      (ClamAV 비동기 스캔 — INSTREAM TCP)
       │
       ▼
[7] AsyncThumbnailService (이미지면 백그라운드 썸네일 400px JPG 생성)
```

### 4.1 ClamAV 운영 토글

```yaml
gopcms:
  file:
    clamav:
      enabled: false  # ← 토글 (기본값)
```

| 설정 | 빈 분기 | 동작 |
|---|---|---|
| `enabled=false` | NoOpVirusScanQueue | clamd 통신 0건. 신규 파일 영구 PENDING. **PENDING 다운로드 허용** |
| `enabled=true` + clamd 정상 | ClamAvVirusScanQueue | INSTREAM TCP 스캔. CLEAN/INFECTED 전이 |
| `enabled=true` + clamd 미기동 | ClamAvVirusScanQueue | ERROR 전이. retry 스케줄러가 PENDING 재시도 |

다운로드 허용 정책 (`VirusScanStatus.isDownloadable()`):
- `CLEAN` ✓ — ClamAV 안전 판정
- `PENDING` ✓ — 스캔 대기 / NoOp 모드 (6중 방어 통과 격리 파일)
- `INFECTED` ✗ — 바이러스 검출. 관리자 강제 다운로드(`adminDownload`) 만 가능
- `ERROR` ✗ — 스캔 인프라 장애. 결과 미상이므로 보수 차단

---

## 5. download_auth 7단계 정책 (2026-05-01)

### 5.1 정책 매트릭스

| 정책 | 라벨 | 비회원 | MEMBER | 작성자 본인 | EMPLOYEE | STAFF | PRIVACY 단독 | MANAGER | ADMIN |
|---|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| `ANONYMOUS` | 누구나 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `ROLE_MEMBER` | 회원 | ✗ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✓ |
| `ROLE_EMPLOYEE` | 직원 | ✗ | ✗ | ✗ | ✓ | ✓ | ✗ | ✓ | ✓ |
| `ROLE_STAFF` | STAFF | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✓ | ✓ |
| **`OWNER_PRIVACY`** | 개인정보관리자 | ✗ | ✗ | **✓** | ✗ | ✗ | **✓** | ✗ ★ | ✗ ★ |
| `ROLE_MANAGER` | 책임자 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✓ |
| `ROLE_ADMIN` | 전체관리자 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ |

★ **OWNER_PRIVACY 만 ROLE_ADMIN 자동 통과 안 됨** — closure 단절(ROLE_PRIVACY parent NULL) + ROLE_PRIVACY 명시 부여만 통과.

### 5.2 평가 흐름 — `enforceDownloadAuth()`

```java
private void enforceDownloadAuth(String fileGroupId, String fileIdForLog) {
  if (group == null) return;
  String policy = group.getDownloadAuth();
  if (policy == null || policy.isBlank() || "ANONYMOUS".equals(policy)) return;

  Authentication auth = SecurityContextHolder.getContext().getAuthentication();
  if (!authenticated) throw new InsufficientAuthenticationException(...);   // → 로그인 redirect

  // ★ OWNER_PRIVACY — 다른 정책 평가보다 먼저
  if ("OWNER_PRIVACY".equals(policy)) { enforceOwnerPrivacy(group, ...); return; }

  if ("ROLE_MEMBER".equals(policy)) return;   // 인증되었으면 모두 통과

  // 일반 정책 (ROLE_EMPLOYEE / ROLE_STAFF / ROLE_MANAGER / ROLE_ADMIN)
  for (GrantedAuthority ga : auth.getAuthorities()) {
    if ("ROLE_ADMIN".equals(name)) granted=true;             // 안전망 (closure 누락 대비)
    if (policy.equals(name))       granted=true;
  }
  if (!granted) throw new AccessDeniedException(...);
}
```

### 5.3 OWNER_PRIVACY 가드 — `enforceOwnerPrivacy()` (Step 6)

```java
private void enforceOwnerPrivacy(FileGroup group, String fileIdForLog, Authentication auth) {
  String currentUserId = ((CustomUserDetails) principal).getUserId();
  String roleIdsCsv    = ((CustomUserDetails) principal).getRoleIds();

  // (1) ROLE_PRIVACY 보유 — closure 단절이라 admin 자동 통과 안 됨
  if (containsCsvToken(roleIdsCsv, rolePrivacyId)) {
    auditPrivacyDownload(groupId, currentUserId, "ROLE_PRIVACY");
    return;
  }

  // (2) BBS 작성자 본인 — entity_type='BBS' 일 때만 의미
  if ("BBS".equals(group.getEntityType())) {
    String writerId = boardArticleService.findCreatedBy(articleId);
    if (writerId != null && !writerId.isBlank()
        && !OWNER_GUARD_SENTINELS.contains(writerId.toUpperCase())   // ""/ALL/ANONYMOUS/SYSTEM 제외
        && writerId.equals(currentUserId)) {
      auditPrivacyDownload(groupId, currentUserId, "OWNER");
      return;
    }
  }

  throw new AccessDeniedException("개인정보관리자 또는 본인만 다운로드할 수 있습니다.");
}
```

**의도된 보안 동작**:
1. **ROLE_ADMIN 도 OWNER_PRIVACY 자동 통과 안 됨** — `tb_role.parent_role_id=NULL` 으로 closure 단절. admin 의 role_ids CSV 에 ROLE_PRIVACY UUID 가 들어가지 않음
2. **CSV 토큰 정확 매치** — `containsCsvToken()` 으로 substring 매칭 사고 차단
3. **created_by sentinel 가드** — `""` / `"ALL"` / `"ANONYMOUS"` / `"SYSTEM"` 같은 비실명 값은 본인 인정 안 함. 시드/시스템 계정 글의 대량 owner-bypass 사고 차단
4. **감사 추적 강화** — OWNER_PRIVACY 통과 시 `FILE_PRIVACY_DOWNLOAD` 별도 이벤트 + `actor`/`reason` (OWNER vs ROLE_PRIVACY) 기록

### 5.4 설정값

```yaml
gopcms:
  security:
    role-privacy-id: 00000000-0000-7000-8000-000000000018  # @Value default
```

운영 환경별 다른 UUID 가 필요하면 환경변수 오버라이드.

### 5.5 공지글 ANONYMOUS 우선 (게시판 도메인 측 정책)

본 file 도메인은 `download_auth` 값을 그대로 평가만. 정책 결정은 게시판 도메인이 담당.

[BoardArticleServiceImpl.resolveArticleDownloadAuth()](../src/main/java/com/gonet/primary/board/article/service/BoardArticleServiceImpl.java) 가:
- `notice_yn='Y'` → `"ANONYMOUS"` 강제
- `notice_yn='N'` → `master.downloadAuth` 그대로

→ 본 도메인은 그 결과를 file_group.download_auth 에 그대로 INSERT/UPDATE.

---

## 6. option A — `ensureGroup()` 패턴 (2026-05-01)

### 6.1 문제

기존 흐름: file-picker 가 파일 업로드 시 group 을 lazily 생성 → DB DEFAULT `ROLE_MEMBER` 박힘. 도메인 Service 가 form 저장할 때 정책 변경해야 함. **지연 윈도우 동안 잘못된 정책** 적용.

### 6.2 해결

`FileService.ensureGroup(entityType, entityId, siteId, downloadAuth)` 단일 진입점:

```
- 그룹 없음 → 신규 INSERT (download_auth = 요청 정책)
- 그룹 있음 + 정책 동일 → no-op
- 그룹 있음 + 정책 다름 → updateDownloadAuth (cascade)
```

### 6.3 도메인별 사용

| 도메인 | 정책 | 호출 위치 |
|---|---|---|
| **popup** | `ANONYMOUS` 강제 | `PopupServiceImpl.create()` / `update()` 직후 |
| **banner** | `ANONYMOUS` 강제 | `BannerServiceImpl.create()` / `update()` 직후 |
| **board(BBS)** | master.downloadAuth (공지글이면 ANONYMOUS) | `BoardArticleServiceImpl.create()` / `update()` 의 `ensureFileGroup()` |
| **content / member 프로필 / employee** | 미적용 (DB DEFAULT `ROLE_MEMBER`) — 후속 PR |

### 6.4 마스터 download_auth 변경 시 cascade

```java
// BoardMasterServiceImpl.update() — download_auth 변경 감지 시
if (!Objects.equals(prevDownloadAuth, nextDownloadAuth)) {
    int updated = fileGroupMapper.cascadeUpdateDownloadAuthByBbs(
        bbsMasterId, nextDownloadAuth, null, null);
    // 감사: BBS_MASTER_DOWNLOAD_AUTH_CASCADE
}
```

```sql
-- FileGroupMapper.xml
UPDATE tb_file_group g
   JOIN tb_bbs_article a
     ON a.article_id = g.entity_id
    AND a.delete_yn  = 'N'
   SET g.download_auth = #{downloadAuth}, ...
 WHERE g.entity_type   = 'BBS'
   AND g.delete_yn     = 'N'
   AND a.bbs_master_id = #{bbsMasterId}
   AND a.notice_yn     = 'N'      -- ★ 공지글 제외
```

---

## 7. URL 맵

| 영역 | 메서드 | URL | 권한 |
|---|---|---|---|
| **REST 업로드** | POST | `/api/v1/file/upload` (entityType/entityId/siteId 파라미터 + multipart) | AUTHENTICATED |
| **REST 다운로드** | GET / HEAD | `/fileDown/{fileId}` (단일) | enforceDownloadAuth 평가 |
| | GET / HEAD | `/fileDown/group/{fileGroupId}` (ZIP) | 동일 정책 |
| | GET | `/fileDown/{fileId}/thumb` (썸네일) | PERMIT_ALL |
| **관리자** | GET | `/admin/system/file` (목록) | ROLE_STAFF |
| | GET | `/admin/system/file/{fileId}` (상세) | |
| | POST | `/admin/system/file/{fileId}/delete` | |
| | GET | `/admin/system/file/{fileId}/download` (관리자 강제 — INFECTED 도) | ROLE_STAFF |

조건부 헤더 지원:
- `If-None-Match` (weak/strong/콤마/와일드카드 RFC 7232)
- `If-Match` → 412 Precondition Failed
- `If-Modified-Since` (RFC 1123 + RFC 1036 + asctime 파서)
- `If-Range` → validator 일치 시 206, 불일치 시 200 강등
- `Last-Modified` (Files.getLastModifiedTime 초 정밀도)
- `Cache-Control: private, no-store` — 기밀 파일이 공유 캐시에 남지 않게

---

## 8. FileService 메서드 매트릭스

| 메서드 | 동작 | 권한 |
|---|---|---|
| `upload(req, file, dir)` | 6중 방어 → INSERT tb_file → enqueue scan | 인증 (filter) |
| `uploadDocument/Image/Video` | 카테고리별 — 확장자×MIME family 교차 검증 | 인증 |
| `download(fileId, req, res)` | enforceDownloadAuth → 일반 다운로드 + Range 지원 | 정책 평가 |
| `adminDownload(fileId, req, res)` | 상태 무관 + INFECTED 경고 로그 — 관리자 검증용 | ROLE_STAFF |
| `downloadThumbnail(fileId, res)` | 썸네일 응답 — 정책 무관 | PERMIT_ALL |
| `downloadGroup(groupId, req, res)` | enforceDownloadAuth → CLEAN/PENDING 만 ZIP 묶음 | 정책 평가 |
| `softDelete(fileId)` | DB soft delete (FIM 보존) | 관리자 |
| `ensureGroup(entityType, entityId, siteId, downloadAuth)` | option A 진입점 — 그룹 사전 생성/cascade | (Service 내부 호출) |
| `recentDownloads(fileId, limit)` | log_file_download 최근 N건 | (관리자 화면용) |

---

## 9. 감사 이벤트

| 이벤트 | 발생 위치 | 비고 |
|---|---|---|
| `FILE_UPLOAD` | FileServiceImpl.commit() | originalName/sizeBytes/mimeDetected/category/reencoded/thumbnail/fileHash |
| `FILE_DOWNLOAD` | FileServiceImpl.download() | sizeBytes |
| `FILE_DOWNLOAD_GROUP` | downloadGroup() | count/zipBytes |
| `FILE_ADMIN_DOWNLOAD` | adminDownload() | status/sizeBytes |
| `FILE_DELETE` | softDelete() | originalName/sizeBytes |
| **`FILE_PRIVACY_DOWNLOAD`** | enforceOwnerPrivacy() | actor/reason (OWNER vs ROLE_PRIVACY) — 본 이벤트가 별도 발행되어 추적성 이중화 |
| `VIRUS_SCAN_INFECTED` | ClamAvVirusScanQueue | virus/reply/storedPath. result=FAIL |
| `BBS_MASTER_DOWNLOAD_AUTH_CASCADE` | BoardMasterServiceImpl.update() | newAuth/groupsUpdated |

---

## 10. 설정 (`application.yml`)

```yaml
gopcms:
  file:
    upload:
      base-dir: /var/gopcms/files                     # 정식 저장소 (운영 마운트)
      quarantine-dir: /var/gopcms/quarantine          # 격리 저장소
      thumb-dir: /var/gopcms/thumbs
      max-file-size: 200MB
      max-request-size: 250MB
      allowed-extensions:
        any: [pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, jpg, jpeg, png, gif, webp, mp4, mov, ...]
        document: [pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, txt, md]
        image: [jpg, jpeg, png, gif, webp]
        video: [mp4, mov, avi, mkv, webm]
    clamav:
      enabled: false                                  # ★ 토글
      host: ${PCMS_CLAMAV_HOST:127.0.0.1}
      port: ${PCMS_CLAMAV_PORT:3310}
      connect-timeout-ms: 5000
      read-timeout-ms: 60000
      max-stream-bytes: 26214400                      # 25MB — clamd StreamMaxLength 일치
      executor:
        core-pool: 2
        max-pool: 4
        queue-capacity: 500
      retry:
        enabled: true
        cron: "0 */10 * * * *"
        batch-size: 200
        stale-minutes: 5
    purge:
      cron: "0 0 4 * * *"
      retention-months: 6

  security:
    role-privacy-id: 00000000-0000-7000-8000-000000000018
```

---

## 11. file-picker fragment 사용

### 11.1 호출 패턴

```html
<th:block th:replace="~{fragments/file-picker :: picker(
    inputName='attachmentsJson',
    category='ANY',
    multiple=true,
    maxFiles=${master.fileCountMax},
    entityType='BBS',
    entityId=${form.articleId},
    siteId=${master.siteId})}"/>
```

### 11.2 동작

1. picker 가 multipart 업로드 → `/api/v1/file/upload` (entityType/entityId 포함)
2. 서버: `FileServiceImpl.commit()` 이 group `findByEntity` → 없으면 INSERT (DB DEFAULT `ROLE_MEMBER`)
3. picker hidden input 에 fileId JSON 배열 누적
4. 호출 폼 POST 시 hidden input 의 fileId 들이 form.attachmentsJson 으로 전송
5. Service 가 `ensureGroup(entityType, entityId, siteId, 정책)` 으로 그룹 정책 동기화
6. `syncAttachments(fileGroupId, keep)` — picker 에서 X 로 뺀 파일은 soft delete

### 11.3 사전 발급 entityId

- 작성 폼 GET 시점에 도메인 PK 사전 발급 (`UuidV7Generator.generate(prefix)`)
- 폼 hidden 으로 전송 + picker `entityId` 로도 사용
- 폼 cancel 시 orphan group/file → FilePurgeService 일반 purge 정리

---

## 12. 운영 시나리오

### 12.1 신규 업로드 (clamav 비활성)

```
1. 회원이 파일 업로드 → /api/v1/file/upload
2. 6중 방어 통과
3. INSERT tb_file (virus_scan_status='PENDING')
4. NoOpVirusScanQueue.enqueue → log only, 상태 PENDING 유지
5. 회원 또는 익명(정책상 허용 시) 다운로드 → 200 ✓ (PENDING 허용)
```

### 12.2 OWNER_PRIVACY 게시판 흐름

```
1. QNA 게시판 마스터 download_auth=OWNER_PRIVACY 등록
2. 회원 A 가 글 + 첨부 작성
   → BoardArticleServiceImpl.create() → ensureFileGroup(.., 'OWNER_PRIVACY')
   → tb_file_group.download_auth='OWNER_PRIVACY'
3. 회원 B 다운로드 시도 → enforceOwnerPrivacy() → ROLE_PRIVACY 미보유 + writerId≠B → AccessDenied 403
4. 회원 A 본인 시도 → writerId==A → 통과 + log FILE_PRIVACY_DOWNLOAD reason=OWNER
5. ROLE_ADMIN(PRIVACY 미부여) → 403 ★ 자동 통과 안 됨
6. ROLE_PRIVACY 부여자 → 통과 + log reason=ROLE_PRIVACY
7. STAFF 가 그 글에 "공지" 토글 ON
   → file_group.download_auth = 'ANONYMOUS' (BoardArticleServiceImpl.adminToggleNotice cascade)
8. 비회원 시도 → 200 ✓ (공지글 정책 우선)
```

### 12.3 마스터 download_auth 변경 cascade

```
1. 게시판 download_auth: ROLE_MEMBER → ROLE_ADMIN 변경
2. BoardMasterServiceImpl.update()
   → fileGroupMapper.cascadeUpdateDownloadAuthByBbs(bbsMasterId, 'ROLE_ADMIN')
   → 일반글 file_group → ROLE_ADMIN
   → 공지글 file_group 은 ANONYMOUS 보존 (★ WHERE notice_yn='N')
3. 일반회원의 일반글 첨부 → 403
4. 일반회원의 공지글 첨부 → 200 ✓
```

### 12.4 ERROR 파일 복구

`enabled=true` + clamd 미기동으로 ERROR 박힌 파일 일괄 복구:

```sql
UPDATE tb_file
   SET virus_scan_status = 'PENDING',
       updated_at        = NOW() - INTERVAL 10 MINUTE
 WHERE virus_scan_status IN ('PENDING','ERROR')
   AND delete_yn = 'N';
```

`updated_at` 을 stale cutoff 이전으로 → retry 스케줄러 다음 사이클(5분 이내) 자동 픽업.

---

## 13. 알려진 한계 (후속 PR 후보)

1. ~~다운로드 권한 정책 정적 4-enum~~ — 7-enum 으로 확장 완료 (2026-05-01)
2. ~~OWNER_PRIVACY 가드 미구현~~ — Step 6 완료 (2026-05-01)
3. **content / member / employee 도메인 ensureGroup() 미적용** — 후속 PR
4. **`master.fileSizeMax` Service 단 미시행** — picker 클라이언트 검증만, 서버 단 검증은 후속 PR
5. **공격벡터 자동 테스트 20종** — S3-4 별도 스프린트
6. **EXIF 제거** — 이미지 재인코딩 시 EXIF 도 함께 제거하지만 명시 정책 없음
7. **ZIP 재귀 검사** — clamd 의 archive bomb detection 옵션 의존
8. **Prometheus 메트릭** — virus_scan_status 별 카운터 / clamd latency 노출 미설정
9. **그룹 ZIP 의 OWNER_PRIVACY** — 동일 정책 평가됨. 본인 글의 모든 첨부 일괄 zip 가능 (의도)

---

## 14. 변경 이력

| 일자 | 변경 |
|---|---|
| 2026-04-24 | 6중 방어 스택 + REST API + 다운로드 엔진 (S3-2/3) |
| 2026-04-25 | ClamAV 실연동 (INSTREAM TCP) + 비동기 스캔 + retry 스케줄러 + log_file_download |
| 2026-04-25 | 3-DB `@Qualifier` 치명 버그 해소 (TX 매니저 분리) |
| 2026-04-27 | `tb_file_group.download_auth` 4단계 정책 도입 + 썸네일 PERMIT_ALL + PENDING 다운로드 허용 |
| 2026-04-28 | 통합 스케줄러 패키지 (`com.gonet.scheduler`) — 4종 + AccessStat 추가로 5종 |
| 2026-05-01 | **download_auth 7단계 확장** (ANONYMOUS / ROLE_MEMBER / ROLE_EMPLOYEE / ROLE_STAFF / OWNER_PRIVACY / ROLE_MANAGER / ROLE_ADMIN) |
| 2026-05-01 | **OWNER_PRIVACY 가드 + ROLE_PRIVACY 보유 체크 + created_by sentinel 가드** (Step 6) |
| 2026-05-01 | **option A `ensureGroup()` 패턴** — popup/banner ANONYMOUS 자동 cascade |
| 2026-05-01 | **VARCHAR(40) ID + 도메인 prefix** (FIL_/FG0_) |
| 2026-05-01 | **공지글 ANONYMOUS 우선 정책** — 게시판 도메인이 정책 결정, 본 도메인은 평가만 |
| 2026-05-01 | **ClamAV 운영 토글 정합화** — `enabled=false` 기본값 + INFECTED/ERROR 차단 / CLEAN+PENDING 허용 |
