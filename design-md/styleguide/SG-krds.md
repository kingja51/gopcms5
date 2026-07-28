# SG · KRDS 순정 — 범정부 디자인시스템 (Tailwind v4 CLI 스타일가이드)

| 항목 | 값 |
|---|---|
| 대상 | gopcms5 전 사이트 공통 기반 (대학교·연구소·행정 홈페이지) |
| 토큰 원전 | [src/krds.css](../../src/krds.css) — github.com/KRDS-uiux/krds-uiux `tokens/transformed_tokens.json` 이식 |
| 데모 | [SG-krds.html](./SG-krds.html) (SG-krds-output.css 단일 로드) |
| 무드 | KRDS 표준 그대로 — Pretendard GOV · KRDS Blue(#256ef4) · 행간 1.5 · 최대 radius 12px |

> **KRDS 순정 기준선**: 이 SG 가 gopcms5 의 기본값이다. 기관별 시각 언어(DESIGN-*.md 기반
> 템플릿)는 이 위에 `.tpl-*` 레이어로 얹는다 — 토큰 구조·접근성 규칙은 오버라이드 금지.

## 1. 빌드 (Tailwind v4 CLI)

```bash
# 전역(실서비스): 프로젝트 루트에서
npx @tailwindcss/cli -i ./src/krds.css -o ./dist/krds.css --minify

# 스타일가이드(데모 페이지): design-md/styleguide 에서
npx @tailwindcss/cli -i ./SG-krds-input.css -o ./SG-krds-output.css

# 개발 감시
npx @tailwindcss/cli -i ./src/krds.css -o ./dist/krds.css --watch
```

Maven 통합 시 `exec-maven-plugin` 의 `npm run css` 가 `src/krds.css` 를 입력으로
`src/main/resources/static/css/output.css` 를 생성한다 (pom.xml 의 tailwind.skip 토글 참조).
Pretendard GOV woff2 는 `static/fonts/` 에 자체 호스팅(CDN 금지) — 미배치 시 시스템 폰트 폴백.

## 2. 토큰 요약

**브랜드 램프(KRDS Blue, 스왑 포인트)**:
`5 #ecf2fe · 10 #d8e5fd · 20 #b1cefb · 30 #86aff9 · 40 #4c87f6 · 50 #256ef4(주) · 60 #0b50d0(hover) · 70 #083891(active) · 80 #052561 · 90 #03163a · 95 #020f27`
— `:root` 의 `--brand-*` 11단계만 바꾸면 재빌드 없이 전체 리컬러. **-50 은 흰 글씨 4.5:1 이상 필수.**

**강조(point)**: KRDS Point Red #d63d4a 램프 — 파괴적 액션·강조 전용.
**시맨틱**: `bg/surface/surface-subtle(r)/fg/fg-strong/fg-subtle/line/divider` — hc(선명한 화면) 모드에서 자동 전환.
**상태색(고정)**: danger/warning/success/info — `*-surface + *-fg` 조합 고정 사용.
**타이포**: `text-display-{sm,md,lg}` `text-heading-{2xs~xl}` `text-body-{xs~lg}` `text-label-{xs~lg}` — 행간 1.5 내장, raw `text-xl` 금지. 본문 기본 17px, 최소 14px.
**radius**: `rounded-{xsmall(2)|small(4)|medium(6)|large(10)|xlarge(12)|max(원형)}` — 최대 12px.
**간격**: 표준 Tailwind(p-2=8px) — 8px 그리드, 4px 는 미세조정 전용.
**그림자**: `shadow-e1~e4` (카드→드롭다운→플로팅→모달) — 평면 콘텐츠는 보더 우선.
**레이아웃**: `max-w-content`(1200px) · 12컬럼 · 브레이크포인트 360/768/1024/1280/1440.

## 3. 테마 (기관 계열 프리셋 — html 클래스)

| 테마 | 클래스 | 주색(50) | 대비 | 용도 예 |
|---|---|---|---|---|
| 블루(기본) | (없음) | #256ef4 | 4.55:1 | 행정·범용 표준 |
| 틸 | `theme-teal` | #0e8074 | 4.82:1 | 병원·해양·환경대학원 |
| 인디고 | `theme-indigo` | #3e4ec4 | 6.79:1 | 연구소·행정 중후 계열 |
| 그린 | `theme-green` | #228738 | 4.57:1 | 농림·환경 계열 |
| 선명한 화면 | `hc` | #003399 | 10.86:1 | 고대비 접근성 모드(테마와 병행) |

## 4. 컴포넌트 레시피 (프리셋 = .krds-*)

```html
<!-- 버튼: 위계 5종 · 화면당 primary 1개 · point 는 파괴적 확정 전용 -->
<button class="krds-btn krds-btn-primary">주 액션</button>
<button class="krds-btn krds-btn-secondary">보조</button>
<button class="krds-btn krds-btn-tertiary">3차</button>
<button class="krds-btn krds-btn-point">삭제</button>

<!-- 폼: 라벨 필수, 오류 = 색+아이콘+텍스트 3중 표현 -->
<input class="krds-input"/> <select class="krds-select"></select>
<textarea class="krds-textarea"></textarea>
<input type="checkbox" class="krds-check"/> <input type="checkbox" class="krds-switch" role="switch"/>

<!-- 표면: 카드·알림·배지·태그 -->
<div class="krds-card">…</div>
<div role="alert" class="krds-alert krds-alert-danger">…</div>
<span class="krds-badge bg-brand-5 text-brand-60">배지</span>

<!-- 내비: 탭·브레드크럼·페이지네이션·스텝 (상태는 aria-*) -->
<div class="krds-tabs" role="tablist"><button class="krds-tab" role="tab" aria-selected="true">탭</button></div>
<ol class="krds-breadcrumb">…</ol>
<div class="krds-pagination"><a class="krds-page" aria-current="page">1</a></div>
<ol class="krds-steps"><li class="krds-step" aria-current="step">입력</li></ol>

<!-- 공개/모달: 네이티브 details/dialog 규약 (JS 라이브러리 불필요) -->
<div class="krds-accordion"><details><summary>질문</summary><div class="krds-accordion-body">답변</div></details></div>
<dialog class="krds-modal">…</dialog>

<!-- 테이블 · 스피너 -->
<table class="krds-table krds-table-zebra"><caption>목록</caption>…</table>
<span class="krds-spinner" role="status" aria-label="불러오는 중"></span>
```

## 5. Do / Don't

**Do** — 색은 토큰·유틸로만(brand/point/gray 3색 원칙) · 타이포는 KRDS 스케일 토큰만 ·
8px 그리드 스냅 · 링크는 색+밑줄 병행 · 상태는 aria 속성으로 표기(스타일이 따라옴) ·
터치 타깃 최소 40px(주요 CTA 48px) · 모든 인터랙션에 focus-visible 링(프리셋 내장).

**Don't** — raw HEX 직접 사용 금지 · raw `text-xl` 등 Tailwind 기본 타이포 금지(본문 계열) ·
radius 12px 초과 금지(원형 제외) · 색만으로 정보 구분 금지 · `--brand-*` 외 임의 색 스왑 금지 ·
상태색(danger 등) 조합 임의 변경 금지 · 그림자 남용 금지(평면은 보더 우선).

## 6. 체크 포인트

① `-50` 대비 4.5:1(흰 글씨) 유지 — 리브랜딩 시 재검증
② hc 토글 시 전 컴포넌트 판독 가능(SG 상단 버튼으로 확인)
③ 폼 오류가 색+아이콘+텍스트 3중 표현인지
④ 테이블 caption·알림 role·모달 dialog 등 시맨틱 마크업 준수
⑤ 본문 최소 폰트 14px · 행간 1.5 유지
