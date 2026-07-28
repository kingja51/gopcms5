-- ============================================================================
-- V905 — ai(인공지능학과) "정부지원과제" 실측 확장 (dev 전용)
-- ----------------------------------------------------------------------------
-- 원전: ai.konyang.ac.kr sub03_02 · sub03_04_01/02 · sub03_05_01 크롤링 (2026-07-28)
-- 구성:
--   · SW중심대학사업(기존 CNT 0506) body 실측 교체
--   · 중소기업계약학과 — 2뎁스 FOLDER + 3뎁스 CONTENT 2종(학과소개·교육과정) ← 3뎁스 실증
--   · 바이오융복합기술 전문인력양성 — 2뎁스 FOLDER + 3뎁스 사업안내(추진체계·전략 이미지 2장)
--   · ICT 학점연계 — menu_type=URL 외부 링크(ictintern.or.kr) ← URL 메뉴 실증
-- 이미지: /images/sites/ai/{bio-plan,bio-strategy}.jpg (실사진 다운로드)
-- ============================================================================

-- ① SW중심대학사업 — 실측 본문 교체
UPDATE tb_content SET
  summary = '과기정통부 SW중심대학 — AX-MASTER 인증제·AI 리터러시·장학 혜택',
  body = '<h2 class="text-heading-md">건양대학교 SW중심대학 사업단</h2>
<p class="text-body-md" style="margin-top:12px">건양대학교는 과학기술정보통신부 주관 ''SW중심대학'' 사업에 선정되어, 급변하는 디지털
대전환 시대에 발맞춰 SW 전문 인력과 융합 인재를 양성하는 혁신의 허브 역할을 수행합니다.</p>
<div class="krds-card" style="margin-top:16px;text-align:center;background:var(--brand-5);border-color:var(--brand-20)">
  <p class="text-heading-sm text-brand-60">"지역과 산업을 선도하는 AI·SW 혁신 인재 양성"</p>
</div>
<div class="grid grid-cols-12 gap-4" style="margin-top:16px">
  <div class="col-span-12 md:col-span-4 krds-card"><span class="krds-badge bg-brand-50 text-fg-on">전문성</span>
    <p class="text-body-sm" style="margin-top:8px">기업 수요를 반영한 실무 중심의 하이테크 교육 과정 운영</p></div>
  <div class="col-span-12 md:col-span-4 krds-card"><span class="krds-badge bg-brand-50 text-fg-on">융합성</span>
    <p class="text-body-sm" style="margin-top:8px">전공을 불문하고 누구나 AI·SW 역량을 갖출 수 있는 융합 교육 시스템</p></div>
  <div class="col-span-12 md:col-span-4 krds-card"><span class="krds-badge bg-brand-50 text-fg-on">공헌</span>
    <p class="text-body-sm" style="margin-top:8px">지역사회 및 산업체와 연계한 SW 가치 확산</p></div>
</div>
<h3 class="text-heading-sm" style="margin-top:32px">벨트형 SW역량인증제 — AX-MASTER</h3>
<p class="text-body-md" style="margin-top:8px">학생들이 단계별로 SW 역량을 쌓을 수 있도록 유도하는 건양대만의 고유 인증제도입니다.</p>
<ul class="text-body-md" style="margin:8px 0 0 20px;list-style:disc">
  <li><strong>단계별 성취</strong>: 기초(White)부터 전문가(Black) 수준까지 단계별 벨트를 부여하여 동기를 부여</li>
  <li><strong>실무 중심</strong>: 네이버 클라우드 아카데미 등 글로벌 IT 기업의 교육 과정을 이수하고 인증</li>
</ul>
<h3 class="text-heading-sm" style="margin-top:32px">전교생 대상 AI 리터러시 교육</h3>
<ul class="text-body-md" style="margin:8px 0 0 20px;list-style:disc">
  <li><strong>입학 전 SW 교육</strong>: 신입생 대상 1학점 사전 교육 실시</li>
  <li><strong>SW 기초 필수화</strong>: 전교생 3학점 이상 SW 기초 교과 의무 이수</li>
  <li><strong>SW 마이크로디그리</strong>: 비전공자도 9학점 이수로 SW 융합 역량을 공식 인증받는 소학위 제도</li>
</ul>
<h3 class="text-heading-sm" style="margin-top:32px">장학 혜택 및 학생 복지</h3>
<ul class="text-body-md" style="margin:8px 0 0 20px;list-style:disc">
  <li>SW중심대학 참여 학과 신입생 전원 <strong>1인당 100만 원</strong> 입학 장학금</li>
  <li><strong>소중마일리지</strong>: 자격증·캠프·특강·동아리활동 참여 시 마일리지(장학금) 지급</li>
  <li><strong>프로젝트 지원</strong>: 산업체 공동 캡스톤 디자인·해커톤 참여 비용 전액 지원</li>
</ul>
<p style="margin-top:24px"><a class="krds-btn krds-btn-secondary" href="https://kyusw.konyang.ac.kr/" target="_blank" rel="noopener">SW중심대학사업단 바로가기 ↗</a></p>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000506';

-- ② 메뉴 — 정부지원과제(MNU 0403) 하위 확장: 2뎁스 폴더 2 + URL 1
INSERT INTO tb_menu (menu_id, site_id, site_code, parent_menu_id,
                     menu_name, menu_type, link_target_id, link_url, sort_order, depth) VALUES
('MNU_01985a10-0000-7000-8000-000000000426','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000403','중소기업계약학과','FOLDER',NULL,NULL,2,2),
('MNU_01985a10-0000-7000-8000-000000000429','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000403','바이오융복합기술 전문인력양성','FOLDER',NULL,NULL,3,2),
-- URL 타입 실증 — 외부 사이트(ICT 학점연계 인턴십 포털)로 이동
('MNU_01985a10-0000-7000-8000-000000000431','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000403','ICT 학점연계','URL',NULL,'https://www.ictintern.or.kr/homepage/system/systemView.do',4,2);

-- ③ 메뉴 — 3뎁스 CONTENT (중소기업계약학과 2종 + 바이오 사업안내)
INSERT INTO tb_menu (menu_id, site_id, site_code, parent_menu_id,
                     menu_name, menu_type, link_target_id, sort_order, depth) VALUES
('MNU_01985a10-0000-7000-8000-000000000427','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000426','학과소개','CONTENT','CNT_01985a10-0000-7000-8000-000000000511',1,3),
('MNU_01985a10-0000-7000-8000-000000000428','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000426','교육과정','CONTENT','CNT_01985a10-0000-7000-8000-000000000512',2,3),
('MNU_01985a10-0000-7000-8000-000000000430','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000429','사업안내','CONTENT','CNT_01985a10-0000-7000-8000-000000000513',1,3);

-- ④ 컨텐츠 3종
INSERT INTO tb_content (content_id, site_id, site_code, menu_id, title, slug,
                        body, summary, status, published_at, version_no) VALUES
('CNT_01985a10-0000-7000-8000-000000000511','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000427',
 '중소기업계약학과 학과소개','contract-dept',
 '<h2 class="text-heading-md">중소기업 계약학과 — 전문기술 인재 양성 <span class="krds-badge bg-brand-5 text-brand-60">석사과정</span></h2>
<p class="text-body-md" style="margin-top:12px">4차 산업 혁명시대를 선도할 <strong>의료인공지능 전문가 양성</strong>을 목표로 한 재교육형 중소기업 계약학과입니다.</p>
<ul class="text-body-md" style="margin:12px 0 0 20px;list-style:disc;display:flex;flex-direction:column;gap:6px">
  <li>의료인공지능 연구 분야 및 의료공학 산업의 발전에 기여할 수 있는 전문인을 육성합니다.</li>
  <li>고도의 이론과 응용능력을 겸비한 의료인공지능기술 융합 전문인을 육성합니다.</li>
  <li>의료인공지능 분야에 적용하여 실제로 인체에 사용할 수 있는 실용 학문을 구축함으로써 정확한 진단·치료를 위한
      장비-재료-공간 등 필요한 의료공학기술의 발전을 통해 생명 연장 및 삶의 질 향상에 기여합니다.</li>
</ul>',
 '재교육형 중소기업 계약학과 — 의료인공지능 전문가 양성 (석사과정)', 'PUBLISHED', current_timestamp(), 1),
('CNT_01985a10-0000-7000-8000-000000000512','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000428',
 '중소기업계약학과 교육과정','contract-curriculum',
 '<h2 class="text-heading-md">교과과정</h2>
<div class="grid grid-cols-12 gap-4" style="margin-top:12px">
  <div class="col-span-6 krds-card text-center"><p class="text-heading-xl text-brand-60">42<span class="text-heading-sm">학점</span></p><p class="text-body-xs text-fg-subtle">석사과정 총 이수</p></div>
  <div class="col-span-6 krds-card text-center"><p class="text-heading-xl text-brand-60">63<span class="text-heading-sm">학점</span></p><p class="text-body-xs text-fg-subtle">박사과정 총 이수</p></div>
</div>
<h3 class="text-heading-sm" style="margin-top:24px">주요 교과목</h3>
<table class="krds-table krds-table-zebra" style="margin-top:8px"><caption>중소기업계약학과 주요 교과목</caption>
<thead><tr><th scope="col" style="width:140px">구분</th><th scope="col">교과목</th></tr></thead>
<tbody>
<tr><th scope="row">공통교과</th><td>논문연구지도(P/NP) · 연구세미나 · 인공지능기술 개발 특론 · 연구방법론 · 임상의학총론</td></tr>
<tr><th scope="row">전공이론(석사)</th><td>인공지능딥러닝 · 의료인공지능 · 바이오센서공학 · 의료빅데이터 딥러닝 · 의료영상처리 · 메디컬 IoT · 의용데이터베이스 · 의료정보시스템 설계 · 모바일헬스케어</td></tr>
<tr><th scope="row">전공이론(박사 추가)</th><td>인공지능을 위한 수학 · 디지털 생체신호처리 · 의료빅데이터를 위한 통계학 · 유저빌리티 엔지니어링 · AI 고급 프로그래밍 · 기계학습 이론 · 데이터마이닝 및 검색 · 심층 강화학습</td></tr>
<tr><th scope="row">현장실습</th><td>취업 연계기업 실무연수 및 논문 실험</td></tr>
</tbody></table>',
 '석사 42학점 · 박사 63학점 — 의료인공지능 중심 교과과정', 'PUBLISHED', current_timestamp(), 1),
('CNT_01985a10-0000-7000-8000-000000000513','SIT_01985a10-0000-7000-8000-000000000302','ai','MNU_01985a10-0000-7000-8000-000000000430',
 '바이오융복합기술 전문인력양성 사업안내','bio-program',
 '<h2 class="text-heading-md">바이오융복합기술 전문인력양성</h2>
<ul class="text-body-md" style="margin:12px 0 0 20px;list-style:disc;display:flex;flex-direction:column;gap:6px">
  <li>빅데이터 기반 연구, 헬스케어의 디지털화 등 바이오헬스 산업의 새로운 패러다임에 대응하여 기업 수요 기반 맞춤 전문인력을 양성합니다.</li>
  <li>디지털 헬스케어, AI기반 바이오소재 개발 등 바이오융복합기술분야의 전문인력을 양성하여 산업계에 공급합니다.</li>
  <li>융·복합 학위 과정을 운영하여 현장 기반 실무형 인재를 양성합니다.</li>
</ul>
<h3 class="text-heading-sm" style="margin-top:24px">추진전략</h3>
<ol class="text-body-md" style="margin:8px 0 0 24px;list-style:decimal;display:flex;flex-direction:column;gap:6px">
  <li><strong>산업계 수요 기반 교육</strong> — 수요조사를 통한 기업 맞춤형 교육과정 개발, 분야별 특화 과정 도입</li>
  <li><strong>융·복합 학위과정 운영</strong> — 바이오기술 중심으로 AI·SW·전자·빅데이터 융합, 산학프로젝트 1인 1과제</li>
  <li><strong>단기 교육과정 강화</strong> — 재직자 직무 고도화·단기 집중 실습·학부생 실무 교육</li>
  <li><strong>고용연계·성과 환류</strong> — 기업설명회·채용박람회 등 채용 연계, 성과 확산 지원</li>
</ol>
<h3 class="text-heading-sm" style="margin-top:24px">이수요건 (디지털헬스케어 특화과정)</h3>
<table class="krds-table" style="margin-top:8px"><caption>이수요건</caption>
<thead><tr><th scope="col">핵심전공</th><th scope="col">선택전공</th><th scope="col">산학연계</th><th scope="col">학위논문</th></tr></thead>
<tbody><tr><td>12학점</td><td>1과목</td><td>산학프로젝트(1인 1과제)</td><td>O</td></tr></tbody></table>
<h3 class="text-heading-sm" style="margin-top:32px">추진체계</h3>
<img src="/images/sites/ai/bio-plan.jpg" alt="바이오융복합기술 전문인력양성 추진체계 도식"
     class="rounded-large border border-line" style="width:100%;margin-top:8px"/>
<h3 class="text-heading-sm" style="margin-top:24px">추진전략 체계도</h3>
<img src="/images/sites/ai/bio-strategy.jpg" alt="바이오융복합기술 전문인력양성 추진전략 도식"
     class="rounded-large border border-line" style="width:100%;margin-top:8px"/>',
 '기업 수요 기반 바이오융복합 전문인력 양성 — 추진체계·전략', 'PUBLISHED', current_timestamp(), 1);
