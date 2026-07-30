# 파일 관리 개발 매뉴얼

gopcms5 파일 도메인(P8)의 기능·기술 매뉴얼이다. 업로드·다운로드·미리보기·정리 배치와
그 근거가 되는 테이블·설정을 다룬다. **파일 관련 코드를 고치기 전에 해당 절을 먼저 읽는다.**

- 기준일: 2026-07-30
- 관련 정본: [PLAN.md](../PLAN.md) §P8 · [conventions.md](conventions.md) ·
  [member-domain.md](member-domain.md)(권한·PII 연계)

> 이 문서는 **구현된 것만** 적는다. 코드와 어긋나면 코드가 정답이다.

---

## 0. 설계 원칙

이 도메인의 모든 결정은 선행 프로젝트의 **웹쉘 침해 경험**에서 나왔다. 네 가지가 축이다.

| 원칙 | 구현 |
|---|---|
| **저장소는 웹루트 밖** | 톰캣이 직접 못 내보낸다 → 모든 다운로드가 컨트롤러를 지난다 |
| **업로드 경로는 하나** | `/api/v1/file/upload` 뿐. 여럿이면 그중 하나만 허술해도 뚫린다 |
| **원본 파일명을 쓰지 않는다** | 저장명은 서버가 정한다(`FIL_…UUIDv7.ext`) → 경로 조작·확장자 위장이 구조적으로 불가능 |
| **검사 전 파일은 정식 저장소에 두지 않는다** | 격리 디렉터리에서 전부 검사한 뒤에만 이동 |

### 0.1 전체 흐름

```
[업로드]
  브라우저(file-picker.js)
    └─ POST /api/v1/file/upload   (파일 1개당 1요청, 병렬)
         ├ ① entityType 화이트리스트
         ├ ② 권한 (ROLE_MEMBER / 에디터는 ROLE_STAFF)
         ├ ③ 그룹 확보 — download_auth 는 서버가 정한다
         ├ ④ 개수 상한
         ├ ⑤ ▼ 다중 방어 파이프라인 ▼
         │    격리 저장 → 확장자 → 매직바이트 → 이미지 재인코딩 → 해시 → 정식 이동
         ├ ⑥ tb_file INSERT (virus_scan_status=PENDING)
         ├ ⑦ 썸네일 (실패해도 업로드는 유효)
         └ ⑧ 백신 큐 enqueue (비동기)

[다운로드]
  GET /file/{fileId}
    ├ tb_file_group.download_auth 판정 (FileAccessGuard)
    ├ virus_scan_status 판정 (CLEAN·PENDING 만 통과)
    ├ download_count++
    ├ 스트리밍 (첨부는 무조건 octet-stream)
    └ log_file_download 적재 (거부도 남긴다)
```

---

## 1. 테이블

### 1.1 `tb_file_group` — 소유·권한의 단위

파일 하나하나가 아니라 **묶음**이 권한을 갖는다. 게시글 1건의 첨부 5개는 한 그룹이고,
공개 범위는 그룹 단위로 정해진다.

| 컬럼 | 설명 |
|---|---|
| `file_group_id` | `FGR_` + UUIDv7 |
| `entity_type` | 소유 도메인 — `BBS` `CONTENT` `BANNER` `POPUP` `MEMBER` `EDITOR` `ETC` |
| `entity_id` | 소유 엔티티 ID — **다형 참조라 FK 가 없다** |
| `site_id` / `site_code` | 사이트 스코프(전역 자료는 NULL) |
| `download_auth` | 다운로드 최소 권한 — [§4.2](#42-download_auth-7단계) |
| `delete_yn` | soft delete |

`(entity_type, entity_id)` 로 그룹을 찾는다. FK 가 없는 이유는 소유 대상이 게시글·컨텐츠·
배너 등으로 갈리기 때문이고, 그 대가로 **고아 그룹**이 생길 수 있어 정리 배치가 회수한다
([§6.2](#62-고아-그룹-회수)).

### 1.2 `tb_file` — 파일 1건

| 컬럼 | 설명 |
|---|---|
| `file_id` | `FIL_` + UUIDv7. **저장 파일명의 기준이기도 하다** |
| `original_name` | 사용자가 준 이름 — **표시용일 뿐 신뢰하지 않는다** |
| `stored_name` | `{file_id}.{ext}` — 서버가 정한다 |
| `stored_path` | 정식 저장소 기준 상대 경로 (`yyyy/MM/dd/…`) |
| `thumbnail_path` | 썸네일 경로(비이미지·생성 실패 시 NULL) |
| `extension` | 소문자 확장자 |
| **`mime_detected`** | **Tika 매직바이트 판별값 — 이후 모든 판단의 기준** |
| `mime_client` | 클라이언트가 신고한 Content-Type — **참고용, 신뢰 금지** |
| `size_bytes` | 재인코딩 **이후** 크기 |
| `file_hash` | SHA-256 — 무결성 점검·중복 판정. 재인코딩 이후 내용 기준 |
| `original_content` | 본문 추출 보관용 — **컬럼만 있고 쓰는 코드가 없다**([§10](#10-알려진-제약주의)) |
| `is_image_yn` / `reencoded_yn` | 이미지 여부 / 재인코딩 완료 여부 |
| `virus_scan_status` | `PENDING` `CLEAN` `INFECTED` `ERROR` `QUARANTINED` `RESCANNING` |
| `download_count` | 다운로드 횟수 |
| `sort_order` | 그룹 내 순서 |
| `delete_yn` | soft delete — 물리 삭제는 배치가 한다 |

FK: `file_group_id` → `tb_file_group`. 인덱스는 `(file_group_id, sort_order)`,
`file_hash`, `(virus_scan_status, created_at)`.

### 1.3 `log_file_download` (logging_db) — 다운로드 이력

| 컬럼 | 값 |
|---|---|
| `download_type` | `SINGLE` `GROUP_ZIP` `ADMIN` |
| `result` | `SUCCESS` `FAIL` **`BLOCKED`** |
| `actor_*` | 취급자 ID·유형·로그인 ID |
| `file_*` `original_name` `extension` `size_bytes` | 대상 정보 |
| `request_uri` `client_ip` `user_agent` `trace_id` | 요청 맥락 |

PK 가 `(id, downloaded_at)` 복합인 것은 시간 파티셔닝 대비다(현재 파티션은 없다).

> **`BLOCKED` 이 중요하다.** 성공만 남기면 "한 계정이 남의 비공개 자료를 반복해서
> 두드리는" 패턴이 이력에 나타나지 않는다. 거부 기록이 사고 조사에서 먼저 찾는 것이다.
> CHECK 제약이 `DENIED` 를 허용하지 않아 실제로 적재가 통째로 실패한 적이 있다 — 값은
> 반드시 `BLOCKED`.

---

## 2. 업로드

### 2.1 진입점은 하나

```
POST /api/v1/file/upload   첨부       — ROLE_MEMBER 이상(로그인 회원)
POST /api/v1/file/image    본문 이미지 — ROLE_STAFF 이상 + IMAGE 카테고리 고정
```

`FileApiController` — 순수 JSON. 화면 조각은 Usr/Adm 컨트롤러의 몫이라는 규약을 지킨다.

**파일 1개당 1요청**으로 보낸다(picker 가 병렬 전송). 한 요청에 20개를 묶으면 1개가
거부될 때 나머지도 함께 실패하고, 진행률을 파일 단위로 보여줄 수 없다.

응답:

```json
{ "fileId":"FIL_…", "fileGroupId":"FGR_…", "originalName":"보고서.pdf",
  "sizeBytes":102400, "extension":"pdf", "image":false,
  "url":"/file/FIL_…", "thumbUrl":null }
```

### 2.2 `downloadAuth` 를 받지 않는다

업로드 요청이 공개 범위를 지정할 수 있게 두었더니, 로그인만 하면 **남의 `entityId` 로
업로드하면서 그 그룹 정책을 `ANONYMOUS` 로 낮출 수 있었다**(실측: `OWNER_PRIVACY` 첨부가
공개로 바뀜). 지금은:

- 기존 그룹이면 **정책을 그대로 둔다**
- 새 그룹이면 가장 좁은 기본값 — 에디터는 `ANONYMOUS`(본문에 박히므로 공개일 수밖에 없다),
  그 외는 `ROLE_MEMBER`
- 정책 변경은 도메인 서비스가 `ensureGroup()` 을 직접 부르는 **서버 내부 경로**로만

### 2.3 남의 묶음에 끼워 넣기 차단

`entityId` 는 클라이언트가 보낸다. 그것만 알면 남의 글에 파일을 붙일 수 있으므로,
그룹을 만든 사람이 아니면 거부한다. 단 `ROLE_STAFF` 이상은 운영상 남의 글을 손봐야 하는
일이 있어 열어 두고, 그 행위는 감사 컬럼에 남는다.

---

## 3. 다중 방어 파이프라인

`common/file/service/FileUploadServiceImpl.pipeline()` — **순서 자체가 방어다.**
값싼 검사를 먼저 해서 대부분을 걸러내고, 파일을 실제로 읽어야 하는 검사를 뒤에 둔다.
전 과정을 **격리 디렉터리**에서 수행한 뒤에만 정식 저장소로 옮긴다.

어느 단계에서 실패하든 격리본은 지운다(`finally { storage.discard(…) }`). 실패한 업로드의
잔해가 쌓이면 그 자체가 나중의 사고 지점이 된다.

### ① 파일명·확장자 — `FileExtensionValidator`

파일명 **자체가 공격 표면**이다.

| 차단 | 이유 |
|---|---|
| 널바이트 `a.jpg\0.jsp` | 하위 계층에서 문자열이 잘려 다른 확장자가 된다 |
| 경로 요소 `../` `/` `\` | 저장 경로를 벗어난다 |
| 255자 초과 | |
| 확장자 없음 | |
| **이중 확장자** `a.jsp.jpg` | 마지막만 보지 않고 **모든 마디**를 검사한다 |
| 실행 형식 (`NEVER_ALLOWED`) | jsp/php/asp/exe/sh/py/jar/js/hta 등 60여 종 — 위치 무관 |
| 화이트리스트 밖 확장자 | 카테고리별 목록(`application.yml`) |

### ② 매직바이트 교차 검증 — `TikaMimeDetector`

확장자와 Content-Type 은 **둘 다 사용자가 정한다.** 실제 내용만이 못 속이는 값이라,
여기서 나온 MIME 이 이후 모든 판단의 기준이 된다.

**fail-closed 다.** 처음 구현에서는 일부 확장자에만 기대 MIME 을 두고 나머지를 통과시켰다.
그 결과 **웹쉘을 `.docx`·`.hwp`·`.xlsx` 로 이름만 바꾸면 그대로 업로드됐다**(실측).
지금은 교차검증 표에 없는 확장자를 **거부**한다.

> 이 프로젝트는 `tika-core` 만 쓴다(파서 없음). 그래서 컨테이너 계열은 개별 포맷이 아니라
> **컨테이너 종류**로 판별된다 — OLE2(doc·xls·ppt·hwp)는 `application/x-tika-msoffice`,
> OOXML(docx·xlsx·pptx·hwpx)은 `application/x-tika-ooxml`. 방어에는 충분하다: 웹쉘은
> 텍스트라 이 값들과 절대 일치하지 않는다.

**화이트리스트(yml)와 교차검증 표(코드)는 항상 같은 집합이어야 한다.** yml 에만 확장자를
추가하면 업로드가 거부된다 — 조용히 열리는 대신 눈에 띄게 막히도록 한 의도된 동작이다.

### ③ 이미지 재인코딩 — `ImageReencoder`

이미지에는 픽셀 외의 것이 얼마든지 들어간다(EXIF 주석 속 스크립트, 다중 형식 파일).
**디코딩해서 픽셀만 다시 쓰면** 원본에 무엇이 붙어 있었든 사라진다. 서명 검사보다
확실한 것은 원본을 버리는 것이다.

- 대상: `jpg` `jpeg` `png` `bmp`
- **재인코딩 실패 = 업로드 거부.** 디코딩되지 않는 이미지는 이미지가 아니다
- 재인코딩 대상이 아닌 `gif`·`webp` 는 `ImageIntegrityValidator` 가
  **"형식이 선언한 끝 == 실제 파일의 끝"** 을 확인한다 — GIF89a 뒤에 코드를 이어 붙인
  폴리글롯이 매직바이트만으로는 통과하기 때문이다(실측으로 잡은 구멍).
  GIF 는 트레일러 `0x3B`, WEBP 는 RIFF 헤더의 선언 크기를 실제 길이와 대조한다

> Thumbnailator 는 대상 파일명이 `outputFormat` 과 다르면 확장자를 덧붙여 **다른 이름으로
> 저장한다**(실측: `.png.re` → `.png.re.png`). 그래서 `toFile()` 이 아니라
> `toOutputStream()` 으로 쓴다.

### ④ SHA-256 — `Sha256Hasher`

**재인코딩 이후**의 최종 내용 기준이어야 한다. 무결성 점검(FIM)과 중복 판정에 쓴다.

### ⑤ 정식 이동 — `FileStorage`

```
격리:  {quarantine-dir}/{file_id}.{ext}
정식:  {base-dir}/yyyy/MM/dd/{file_id}.{ext}
썸네일:{thumb-dir}/yyyy/MM/dd/{file_id}.{ext}
```

- **날짜 폴더로 쪼갠다.** 한 디렉터리에 수십만 개가 쌓이면 파일시스템 조회가 급격히
  느려지고 백업·정리도 어려워진다
- `promote()` 는 복사가 아니라 **이동** — 격리본이 남지 않는다
- `contain()` 이 조립 경로가 뿌리 밖으로 나가지 않는지 확인한다. 지금은 우리가 만든
  값만 들어오지만, 경로 탈출은 한 번 뚫리면 임의 파일 읽기가 된다

---

## 4. 다운로드

### 4.1 엔드포인트 — `/file/**`

| URL | 용도 |
|---|---|
| `GET /file/{fileId}` | 원본 다운로드 (`attachment`) |
| `GET /file/{fileId}/thumb` | 썸네일 |
| `GET /file/{fileId}/view` | 미리보기 (`inline`) |

**셋 다 같은 권한 판정을 받는다.** 썸네일만 공개하면 비공개 자료의 내용이 작게나마 새고,
미리보기만 열어 두면 그대로 샌다 — 어느 한쪽이 느슨해지면 `download_auth` 정책 전체가
무의미해진다.

URL 규칙은 `/file/**` **PERMIT_ALL**(priority 75)이다. 파일별 권한은 URL 이 아니라
`tb_file_group.download_auth` 가 정하기 때문이다.

### 4.1b 업로드 권한 — `ROLE_MEMBER`

로그인한 회원이면 올릴 수 있다. `ROLE_PRIVACY` 만 가진 계정은 계층이 끊겨 있어 통과하지
못한다(의도된 결과).

**`ROLE_REAL` 을 기준으로 쓰지 않는다**(2026-07-30 사용자 확정). 그 역할은 "실명확인을
거쳤다" 는 **사실 표시**이고 기능 권한의 기준이 아니다.

> **기준으로 삼을 수도 없었다.** `vw_user_login` 이 회원의 `role_codes` 를
> `'ROLE_MEMBER'` **리터럴로 고정**하고 있다(V6 의 "보류: ROLE_REAL 부여 방식은 후속 결정"
> 주석). `role_ids` 에는 ROLE_REAL 이 들어가지만 Security 권한 목록(`role_codes` 기반)에는
> 절대 나타나지 않으므로, `hasRole("ROLE_REAL")` 이 회원에게 **항상 false** 였다.
> 그 결과 **회원의 첨부 업로드가 전면 차단**돼 있었다 — 실측 확인:
> 수정 전 403 `실명인증 회원 이상만 파일을 올릴 수 있습니다.` → 수정 후 200.
> 코드 리뷰(2026-07-30)에서 드러난 결함이다.

**두 축이 담는 값이 다르다** — 섞어 쓰지 말 것:

| 축 | 근거 컬럼 | ROLE_REAL 포함? |
|---|---|---|
| Security 권한(`hasRole`) | `role_codes` | ❌ 회원은 `ROLE_MEMBER` 리터럴 |
| URL 접근 규칙(`tb_role_url_access`) | `role_ids` | ✅ 전개 CSV 에 포함 |

### 4.2 `download_auth` 7단계

`FileAccessGuard.enforceDownload()` — **권한 판정의 단일 진입점**.

| 값 | 통과 조건 |
|---|---|
| `ANONYMOUS` | 무조건 |
| `ROLE_MEMBER` | 인증되었으면 |
| `ROLE_STAFF` `ROLE_MANAGER` `ROLE_ADMIN` | 해당 역할 또는 `ROLE_ADMIN` |
| **`OWNER_PRIVACY`** | **작성자 본인 또는 `ROLE_PRIVACY`** |
| ~~`ROLE_EMPLOYEE`~~ | CHECK 제약엔 있으나 **쓰지 않기로 확정** — 화면 선택지에서 제외 |

**`OWNER_PRIVACY` 에서는 `ROLE_ADMIN` 이 자동 통과하지 않는다.** 그것이 이 정책의 존재
이유다 — 운영 관리자라도 남의 개인정보 첨부(민원글 등)를 열 수 없어야 하고, 필요하면
`ROLE_PRIVACY` 를 명시적으로 부여받아야 한다(부여 사실이 기록에 남는다).

`created_by` 가 `ALL`·`ANONYMOUS`·`SYSTEM`·`GUEST` 같은 시드 값이면 **본인으로 인정하지
않는다.** 그런 값이 다수 행에 반복되면 한 계정이 남의 자료를 모두 여는 사고가 된다.

비로그인은 **401**(`InsufficientAuthenticationException`), 권한 미달은 **403** 으로
구분한다 — "로그인하면 열릴 수도 있는 상태"와 "영구 거부"는 다르다.

### 4.3 검사 상태 판정

`VirusScanStatus.isDownloadable()` — **`CLEAN` 과 `PENDING` 만** 통과한다.

`PENDING` 을 허용하는 것은 백신 미연동 운영을 전제로 한 판단이다(앞의 다중 방어는 이미
통과했다). 나머지 4종(`INFECTED` `ERROR` `QUARANTINED` `RESCANNING`)은 **결과가 안전하다고
확인되지 않았다**는 뜻이므로 막는다 — 모르는 것은 열지 않는다.

관리자 강제 다운로드(`openForAdmin`)는 이 판정을 우회하되 **경고 로그를 남긴다**.

### 4.4 응답 헤더 — 실행을 막는 층

```
Content-Type: application/octet-stream     ← 첨부는 무조건. 검출 MIME 을 그대로 쓰지 않는다
Content-Disposition: attachment; filename="…"; filename*=UTF-8''…
Cache-Control: private, no-store           ← 기밀 자료가 공유 캐시에 남지 않게
X-Content-Type-Options: nosniff
```

**인라인으로 내보내도 되는 것은 래스터 이미지뿐이다.** SVG 는 이미지처럼 보이지만
스크립트를 담을 수 있어 인라인이면 XSS 가 된다. 지금 화이트리스트에 `svg` 가 없어
업로드될 수 없지만, 나중에 누가 추가했을 때 이 자리가 조용히 열리지 않도록
`safeInlineType()` 이 한 번 더 막는다.

미리보기(`/view`)에는 응답 자체에 CSP 를 붙인다:

```
Content-Security-Policy: default-src 'none'; img-src 'self' data:;
                         style-src 'unsafe-inline'; sandbox
```

### 4.5 다운로드 이력

`FileDownloadLogger` — logging_db 전용 TxManager + `REQUIRES_NEW` 격리.

**이력 적재가 다운로드를 막아서는 안 된다.** logging_db 가 잠깐 불안정하다고 사용자가
파일을 못 받으면 손해가 더 크다. 그래서 별도 트랜잭션으로 떼어내고 예외를 스스로 삼킨다.

권한 판정 실패도 `RESULT_BLOCKED` 로 기록한다(`FileDownUsrController.open()`).

**삼킨 실패는 그대로 묻히지 않는다.** 적재가 깨지면 파일 로그에 더해
`ErrorLogger.logRecordFailure("FILE_DOWNLOAD_LOG", …)` 로 `log_error` 에 남고
관리자 화면(`/adm/error-log`)에서 보인다
([member-domain.md §2.7](member-domain.md#27-삼킨-실패는-log_error-로-끌어올린다)).

이 배선이 필요한 이유는 실측으로 확인됐다 — `'DENIED'` 를 넣었다가 CHECK 제약
(`chk_logfiledl_result`)에 걸려 **기록이 통째로 사라진 적이 있다**(다운로드는 계속됐다).
그때는 아무 화면에도 흔적이 없었다.

---

## 5. 미리보기

### 5.1 원칙 — 가능한 한 서버에서 열지 않는다

문서 파서는 원격코드실행 이력이 길고, 우리가 여는 문서는 전부 외부에서 들어온 것이다.
`ViewerKind` 가 **어디서 파싱하느냐**로 형식을 나눈다.

| 종류 | 확장자 | 파싱 주체 |
|---|---|---|
| `IMAGE` | jpg jpeg png gif webp bmp | 브라우저 |
| `VIDEO` | mp4 webm mov | 브라우저 |
| `TEXT` | txt csv | **아무도 해석하지 않는다**(텍스트로만 표시) |
| `PDF` | pdf | 브라우저(pdf.js) |
| **`HWP`** | hwp hwpx | **rhwp(WASM) — 브라우저 샌드박스 안** |
| **`OFFICE`** | doc docx xls xlsx ppt pptx | **서버(LibreOffice) — 유일** |
| `NONE` | 그 외 | 미리보기 없음 |

HWP 는 `/view` 로 오지 않는다 — 클라이언트가 원본 바이트를 받아 WASM 으로 직접 연다.
그래서 CSP 에 `'wasm-unsafe-eval'` 이 있다(`eval()` 을 여는 `'unsafe-eval'` 과 다르다).

### 5.2 오피스 변환은 기본 꺼짐

```yaml
gopcms.file.viewer.office:
  enabled: ${GOPCMS_VIEWER_OFFICE_ENABLED:false}
  binary: ${GOPCMS_SOFFICE_BIN:soffice}
  timeout-seconds: 60
  cache-dir: ${GOPCMS_VIEWER_CACHE_DIR:./data/gopcms/preview}
```

별도 프로세스·타임아웃·전용 프로파일로 격리한다. **켤 때는 OS 레벨에서 전용 계정·
네트워크 차단을 함께 걸어야 한다 — 앱이 대신해 줄 수 없는 부분이다.**

### 5.3 미리보기 범위는 업로드 정책을 넘지 않는다

목록에 없는 확장자는 `ViewerKind` 가 어떻게 분류하든 `DocumentViewService` 가 `NONE` 으로
떨어뜨린다.

---

## 6. 정리 배치

### 6.1 물리 삭제 — `FilePurgeJob` → `FilePurgeService`

기본 cron `0 0 4 * * *`. **기본이 dry-run** — 스케줄은 돌지만 대상만 로그에 남는다.

| 설정 | 기본 | 뜻 |
|---|---|---|
| `gopcms.file.purge.dry-run` | `true` | **끄면 실제로 지운다** |
| `retention-days` | `180` | soft delete 후 물리 삭제까지 유예 |
| `orphan-group-days` | `7` | 빈 묶음 회수 유예 |
| `batch-size` | `500` | 1회 상한 |

**디스크 먼저, DB 나중.** 순서가 뒤바뀌면 **참조를 잃은 파일**이 디스크에 영원히 남는다.
반대 순서의 사고(행은 지웠는데 파일이 남음)가 더 고치기 어렵다.

썸네일도 함께 지운다 — 원본을 지웠는데 축소본이 남으면 내용이 계속 노출된다.

**단건 독립 트랜잭션**(`FilePurgeWorker`, `REQUIRES_NEW`). 한 건이 실패해도 나머지는
진행하고 실패한 건만 남는다. **워커가 별도 빈인 이유는 자기호출이면 `@Transactional` 이
통째로 무시되기 때문**이다.

결과는 `PurgeResult(scanned, purged, failed, orphanGroups, dryRun)` 로 요약 로그에 남는다.

### 6.2 고아 그룹 회수

저장하지 않고 나간 폼이 빈 그룹을 남긴다(`entity_id` 가 가리키는 대상이 끝내 생기지
않은 경우). `orphan-group-days` 가 지난 빈 묶음을 회수한다.

### 6.3 백신 재검사 — `VirusScanRetryJob`

```yaml
gopcms.file.rescan:
  cron: "0 */10 * * * *"
  stale-minutes: 30
  batch-size: 200
```

`gopcms.file.clamav.enabled=true` 일 때만 잡이 만들어진다. `false` 면
`NoOpVirusScanQueue` 가 들어가고 신규 파일은 `PENDING` 으로 남는다.

---

## 7. 화면

### 7.1 공통 첨부 프래그먼트 — `fragments/file-picker.html` + `file-picker.js`

폼에 넣으면 되는 한 벌. 드래그&드롭·진행률·삭제를 포함한다.

- **CSRF**: 감싸는 `<form>` 의 `_csrf` hidden 을 읽어 `X-CSRF-TOKEN` 헤더로 보낸다
- 파일 1개당 1요청(`XMLHttpRequest`, `upload.onprogress` 로 진행률)
- 프래그먼트 계약:
  `~{fragments/file-picker :: picker(name, entityType, entityId, siteId, category, downloadAuth, maxFiles, files)}`
  — 값은 `data-*` 로 실려 JS 가 읽는다
- 문서 뷰어는 `~{fragments/file-viewer :: viewer(file)}`
- 개수 상한(`gopcms.file.max-files-per-group`, 기본 20)은 **화면과 서버가 같은 값을 본다**.
  폼이 막아도 API 직접 호출이 가능하므로 서버가 다시 센다

### 7.2 문서 뷰어 — `fragments/file-viewer.html` + `file-viewer.js`

HWP(WASM)·PDF(pdf.js)·텍스트·이미지·영상을 한 벌로 처리한다.

### 7.3 관리자 — `/adm/file`

| 화면 | 내용 |
|---|---|
| `list.html` | 목록 — **검사 상태를 먼저 보게** 배치(INFECTED/ERROR 를 놓치면 안 된다) |
| `detail.html` | 메타데이터·무결성 해시·소유 묶음 정책·최근 다운로드 20건 |
| `new.html` | 공통 첨부 폼 시연 |

관리자가 수정할 수 있는 것은 **표시명과 정렬 순서뿐**이다. 저장 경로·해시·MIME 은
방어 판정의 근거라 손대지 않는다.

---

## 8. 설정 일람

```yaml
gopcms.file:
  max-files-per-group: 20
  thumbnail-size: 400
  purge:   { cron, dry-run: true, retention-days: 180, orphan-group-days: 7, batch-size: 500 }
  rescan:  { cron, stale-minutes: 30, batch-size: 200 }
  viewer.office: { enabled: false, binary, timeout-seconds: 60, cache-dir }
  clamav.enabled: false
  upload:
    base-dir:       ${GOPCMS_FILE_BASE_DIR:./data/gopcms/files}
    quarantine-dir: ${GOPCMS_FILE_QUARANTINE_DIR:./data/gopcms/quarantine}
    thumb-dir:      ${GOPCMS_FILE_THUMB_DIR:./data/gopcms/thumbs}
    max-file-size:  200MB
    allowed-extensions:
      any:      [pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, txt, csv,
                 jpg, jpeg, png, gif, webp, bmp, zip, mp4, mov, webm]
      document: [pdf, doc, docx, xls, xlsx, ppt, pptx, hwp, hwpx, txt, csv]
      image:    [jpg, jpeg, png, gif, webp, bmp]
      video:    [mp4, mov, webm]
```

> **`spring.servlet.multipart.max-file-size` 와 반드시 같은 값을 본다.** 기본값 1MB 를
> 그대로 두면 큰 첨부가 컨테이너 단계에서 잘린다. 컨테이너에서 먼저 끊는 편이 이득이다 —
> 디스크에 다 받은 뒤 거부하면 그만큼 자원을 쓴다.

### 8.1 업로드 카테고리

| 카테고리 | MIME 대분류 검사 |
|---|---|
| `IMAGE` | `image/` 접두 |
| `VIDEO` | `video/` 접두 |
| `DOCUMENT` | 없음(대분류가 제각각) |
| `ANY` | 없음 — 확장자 화이트리스트가 유일한 관문 |

알 수 없는 값은 **가장 좁은 `ANY`** 로 떨어진다.

---

## 9. 확장 체크리스트

**새 확장자를 허용할 때** — 셋을 모두 고쳐야 한다.

- [ ] `application.yml` `allowed-extensions` 에 추가
- [ ] **`TikaMimeDetector.ALLOWED` 표에 기대 MIME 추가** (빠뜨리면 업로드가 거부된다)
- [ ] 미리보기가 필요하면 `ViewerKind` 에 분류 추가
- [ ] 실행 가능한 형식이면 애초에 넣지 말 것. `NEVER_ALLOWED` 와 충돌하면 거부된다
- [ ] 인라인 노출이 위험한 형식(svg 등)이면 `safeInlineType()` 확인

**새 소유 도메인(`entity_type`)을 추가할 때**

- [ ] `FileEntityType` 에 상수 추가 (`isValid()` 화이트리스트)
- [ ] 도메인 서비스가 `ensureGroup(entityType, entityId, siteId, downloadAuth)` 로
      **정책을 명시**한다. 업로드 API 는 정책을 정하지 않는다
- [ ] 삭제 시 그룹 정리 경로 확인(안 하면 고아 그룹이 된다)

**새 다운로드 경로를 만들 때**

- [ ] `FileAccessGuard.enforceDownload()` 를 반드시 경유
- [ ] `VirusScanStatus.isDownloadable()` 판정
- [ ] `FileDownloadLogger` 로 성공·거부 모두 기록
- [ ] 첨부는 `octet-stream`, 인라인은 래스터 이미지만

---

## 10. 알려진 제약·주의

| 항목 | 현재 상태 |
|---|---|
| **ClamAV 미연동** | 기본 `false`. 신규 파일은 `PENDING` 이고 그대로 다운로드된다 — 앞의 다중 방어를 통과했다는 전제 위의 운영 판단이다. 운영 전 연동 검토 필요 |
| **ZIP 내용 검사 없음** | 압축은 **절대 풀지 않는다**. 푸는 순간 내부 파일에 대한 방어가 무의미해지기 때문이며, 그 대가로 ZIP 안의 내용은 검사되지 않는다 |
| `GROUP_ZIP` 다운로드 | 로그 CHECK 에는 값이 있으나 **구현되지 않았다** |
| 오피스 미리보기 | 기본 꺼짐. 켜려면 OS 레벨 격리가 선행되어야 한다 |
| `original_content` | **완전 미사용**(실측: Java·XML 어디에도 참조가 없다). 검색·요약용으로 설계됐으나 추출이 배선되지 않았다 |
| 저장소 다중화 | 로컬 파일시스템 전제. 다중 인스턴스면 공유 스토리지(NFS·S3 등)가 필요하다 |
| `log_file_download` 보존 | 36개월 — 파기 배치가 처리한다([member-domain.md](member-domain.md) §9) |

---

## 11. 함정 요약

| 함정 | 증상 | 대응 |
|---|---|---|
| 화이트리스트만 추가 | 업로드가 거부됨 | `TikaMimeDetector.ALLOWED` 도 함께 |
| `download_auth` 를 API 파라미터로 | 남의 첨부가 공개로 바뀜 | 서버가 정한다 |
| 검출 MIME 을 응답에 그대로 | 저장 파일이 브라우저에서 실행 | `octet-stream` 고정 |
| 썸네일만 공개 | 비공개 자료 내용 유출 | 원본과 같은 판정 |
| Thumbnailator `toFile()` | 파일명이 바뀌어 저장 | `toOutputStream()` |
| 자기호출 `@Transactional` | 트랜잭션 무시 | 워커 빈 분리 |
| 로그 CHECK 값 오기 | 적재가 조용히 실패 | `BLOCKED`(`DENIED` 아님) |
| DB 먼저 삭제 | 참조 잃은 파일이 영구 잔존 | 디스크 먼저 |
