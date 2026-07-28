-- ============================================================================
-- V904 — 간호대학 "단과대학 소개" 컨텐츠 실측 반영 (dev 전용)
-- ----------------------------------------------------------------------------
-- 원전: nursingcollege.konyang.ac.kr sub01_01~06.do 크롤링 (2026-07-28)
--   · 학장인사말(전문+학장 사진) · 대학소개(이미지 3종) · 교육목표(목적·목표9·미션/비전·
--     학습성과8) · 대학연혁(1994~2026 전체) · 교수소개(요약 — prog 모듈 접근 불가로 추후)
--     · 찾아오는길(주소·연락처)
-- 이미지: /images/sites/nursingcollege/{dean,intro,education,curriculum}.jpg (실사진 다운로드)
-- 규칙: body 의 클래스는 빌드된 output.css 에 존재하는 krds-*·토큰 유틸만 사용
--   (DB 본문은 Tailwind 스캔 대상이 아님 — 새 유틸 클래스 금지)
-- ============================================================================

-- ① 학장인사말 (greeting)
UPDATE tb_content SET
  summary = '간호대학 학장 임효남 — 인사말',
  body = '<div class="grid grid-cols-12 gap-6">
  <figure class="col-span-12 md:col-span-4">
    <img src="/images/sites/nursingcollege/dean.jpg" alt="간호대학 학장 임효남 교수 사진"
         class="rounded-large border border-line" style="width:100%;max-width:354px"/>
    <figcaption class="text-body-xs text-fg-subtle" style="margin-top:8px;text-align:center">간호대학 학장 임효남</figcaption>
  </figure>
  <div class="col-span-12 md:col-span-8 text-body-md" style="display:flex;flex-direction:column;gap:16px">
    <p class="text-heading-sm text-brand-60">안녕하십니까? 건양대학교 간호대학 방문을 환영합니다.</p>
    <p>건양대학교 간호대학은 1995년 중부권 지역사회의 건강관리분야에서 주도적 역할을 담당할 수 있는 전문간호인 교육을 목적으로 설립되었습니다.</p>
    <p>1999년에 첫 졸업생을 배출하였으며, 2000년 RN-BSN 과정 개설, 2001년 간호학과 대학원 석사과정 개설, 2006년 가정전문간호사 과정, 2007년 노인전문간호사 과정, 2009년 감염관리 전문간호사 과정을 개설하였고, 2014년 간호학 전공 박사학위 과정을 개설하여 간호의 질적 수준과 간호교육의 일원화에 기여하고 있으며, 새로운 도약과 발전을 위해 2014년 8월 간호대학으로 승격하였습니다.</p>
    <p>우리 간호대학은 참된 인성과 통합적 능력을 갖춘 간호인재 양성을 목표로 하고 있으며, 건학 이념에 근거하여 인간존중을 바탕으로 과학적 지식과 치료적 돌봄 능력을 갖추어 대상자의 최적의 안녕을 유지하고 촉진할 수 있는 전문직 간호사를 양성하기 위하여 변화하는 의료서비스 요구에 부응하는 체계적인 통합 교육과정을 운영하고 있습니다.</p>
    <p>대학부속병원에서의 우수하고 차별화된 실습교육을 통해 우리 대학 졸업생들은 우수한 전문직 간호사로, 국내외 임상분야, 교육 및 학계, 보건행정 분야, 지역사회 분야 등 다양한 분야에 진출하여 훌륭한 지도자로서 활동을 하고 있습니다.</p>
    <p>이러한 교육의 결과로 2023년 한국간호교육평가원에서 간호교육프로그램 5년 인증을 받았으며 이를 토대로 간호교육 중심 대학으로 도약하고자 합니다.</p>
    <p>앞으로도 건양대학교 간호대학은 한국의 간호교육 발전뿐 아니라 세계수준의 간호대학으로 발전하기 위해 교직원 모두 헌신적인 노력을 다할 것을 약속 드리며, 이를 위해 학생, 학부모, 동문 및 모든 방문객 여러분의 아낌없는 성원과 격려 부탁드립니다. 감사합니다.</p>
    <p class="text-label-md font-bold" style="text-align:right">간호대학 학장 <span class="text-heading-sm">임효남</span></p>
  </div>
</div>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000701';

-- ② 대학소개 (about)
UPDATE tb_content SET
  summary = '참된 인성과 통합적 능력을 갖춘 간호사 양성의 요람',
  body = '<img src="/images/sites/nursingcollege/intro.jpg" alt="간호대학 전경·실습 이미지"
     class="rounded-large border border-line" style="width:100%"/>
<h2 class="text-heading-md" style="margin-top:24px">참된 인성과 통합적 능력을 갖춘 간호사 양성의 요람</h2>
<p class="text-body-md" style="margin-top:12px">본 간호학과는 1995년 대전·충남권 지역사회의 건강관리체계에서 주도적인 역할을 할 수 있는 전문간호인의 교육을 목적으로 개설되었다. 1999년 제1회 졸업생을 배출하였으며 현재는 690여명이 재학하고 있다.</p>
<h3 class="text-heading-sm" style="margin-top:32px">간호대학의 교육은</h3>
<div class="grid grid-cols-12 gap-4" style="margin-top:12px">
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-5 text-brand-60">특성화 교육</span>
    <p class="text-body-sm" style="margin-top:8px">임상간호 · 글로벌건강간호 · 연구간호 · 보건교사/공무원</p></div>
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-5 text-brand-60">Active Learning</span>
    <p class="text-body-sm" style="margin-top:8px">자기주도적 학습, TBL, PBL, Action Learning, 토의, 발표</p></div>
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-5 text-brand-60">현장중심교육</span>
    <p class="text-body-sm" style="margin-top:8px">지역현장실무기관과 연계한 현장중심 교육과정</p></div>
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-5 text-brand-60">메디컬캠퍼스</span>
    <p class="text-body-sm" style="margin-top:8px">건양대학교의료원·의과대학·의과학대학·의료공과대학 네트워크를 활용한 통합교육</p></div>
</div>
<img src="/images/sites/nursingcollege/education.jpg" alt="간호대학 교육 특징 도식"
     class="rounded-large border border-line" style="width:100%;margin-top:24px"/>
<h3 class="text-heading-sm" style="margin-top:32px">학년별 교육과정</h3>
<ul class="text-body-md" style="margin:12px 0 0 20px;list-style:disc">
  <li>1학년 : 기초과학교육강화</li>
  <li>2학년 : 심화학습 다양화</li>
  <li>3학년 : 현장실무능력증진</li>
  <li>4학년 : 통합적용능력증진</li>
</ul>
<img src="/images/sites/nursingcollege/curriculum.jpg" alt="학년별 교육과정 도식"
     class="rounded-large border border-line" style="width:100%;margin-top:16px"/>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000702';

-- ③ 교육목표 (goal)
UPDATE tb_content SET
  summary = '교육목적 · 교육목표 9 · 미션/비전 · 학습성과 8',
  body = '<h2 class="text-heading-md">교육목적</h2>
<div class="grid grid-cols-12 gap-4" style="margin-top:12px">
  <blockquote class="col-span-12 md:col-span-6 krds-card text-heading-sm text-brand-60" style="text-align:center">『참된 인성을 갖춘 간호사를 양성한다.』</blockquote>
  <blockquote class="col-span-12 md:col-span-6 krds-card text-heading-sm text-brand-60" style="text-align:center">『통합적 능력을 갖춘 간호사를 양성한다.』</blockquote>
</div>
<h2 class="text-heading-md" style="margin-top:32px">교육목표</h2>
<ol class="text-body-md" style="margin:12px 0 0 24px;list-style:decimal;display:flex;flex-direction:column;gap:6px">
  <li>간호전문직 표준에 근거한 간호술을 통합적으로 적용한다.</li>
  <li>인간을 존중하는 의식을 갖춘다.</li>
  <li>언어적, 비언어적인 방법으로 치료적 의사소통 능력을 발휘한다.</li>
  <li>문제해결을 위한 업무 조정 등 전문 분야 간 협력하는 자세를 견지한다.</li>
  <li>간호실무의 법적, 윤리적 기준에 맞는 책임감을 갖는다.</li>
  <li>봉사하는 정신으로 직무에 임한다.</li>
  <li>간호조직 안에서 리더십을 발휘한다.</li>
  <li>비판적 사고에 근거한 간호연구를 기획하고 수행하여 문제를 해결한다.</li>
  <li>국내·외 보건의료 환경변화에 능동적으로 대응한다.</li>
</ol>
<h2 class="text-heading-md" style="margin-top:32px">미션 / 비전</h2>
<div class="grid grid-cols-12 gap-4" style="margin-top:12px">
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-50 text-fg-on">미션</span>
    <p class="text-body-lg font-bold" style="margin-top:8px">참된 인성과 통합적 능력을 갖춘 간호인재 양성</p></div>
  <div class="col-span-12 md:col-span-6 krds-card"><span class="krds-badge bg-brand-50 text-fg-on">비전</span>
    <p class="text-body-lg font-bold" style="margin-top:8px">참된 인성을 갖춘 창의적 글로벌 간호인재 양성</p></div>
</div>
<h2 class="text-heading-md" style="margin-top:32px">학습성과</h2>
<ol class="text-body-md" style="margin:12px 0 0 24px;list-style:decimal;display:flex;flex-direction:column;gap:6px">
  <li>간호학문과 다양한 학문분야의 지식을 응용하여 대상자의 간호상황에 적합한 간호를 제공한다.</li>
  <li>언어적, 비언어적 상호작용을 통한 치료적 의사소통술을 적용한다.</li>
  <li>건강증진과 건강문제 해결을 위해 전문분야 간 협력관계를 개발한다.</li>
  <li>안전과 질 향상 원리를 적용하고 비판적 사고에 근거한 간호과정을 적용하여 임상추론을 실행한다.</li>
  <li>간호전문직 발전을 위해 법과 윤리에 따라 간호를 수행한다.</li>
  <li>간호실무에 필요한 봉사협동정신과 개인과 전문직 발전을 위한 리더십을 발휘한다.</li>
  <li>정보통신과 최신보건의료 기술에 대한 이해를 바탕으로 간호연구를 기획한다.</li>
  <li>국내·외 보건의료정책 변화를 인지하고 보건의료체계 내에서 인구집단 건강을 관리한다.</li>
</ol>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000703';

-- ④ 대학연혁 (history) — 1994~2026 전체 연혁 (연대별)
UPDATE tb_content SET
  summary = '1994 간호학과 신설 인가 → 2014 간호대학 승격 → 2025 KY 시뮬레이션센터 개소',
  body = '<p class="text-body-lg text-fg-subtle">그동안 걸어온 간호대학의 발자취를 소개합니다.</p>
<h2 class="text-heading-md text-brand-60" style="margin-top:24px">2020''s</h2>
<table class="krds-table" style="margin-top:8px"><caption>2020년대 연혁</caption><tbody>
<tr><th scope="row" style="width:120px;white-space:nowrap">2026. 07.</th><td>제7대 학장 임효남 교수 취임(연임)</td></tr>
<tr><th scope="row">2025. 11.</th><td>간호대학 &amp; 건강보험심사평가원 대전충청본부 업무협약(MOU) 체결</td></tr>
<tr><th scope="row">2025. 10.</th><td>KY 시뮬레이션센터 개소</td></tr>
<tr><th scope="row">2025. 03.</th><td>간호학과 30명 증원(정원 197명)</td></tr>
<tr><th scope="row">2024. 11.</th><td>래어달 메디컬 코리아(Laerdal Medical Korea) 간호대학 발전기금 1억원 기탁</td></tr>
<tr><th scope="row">2024. 07.</th><td>한국간호교육평가원 2024년도 간호대학 실습교육 지원사업 선정 · 래어달 메디컬 코리아 MOU · 제6대 학장 임효남 교수 취임</td></tr>
<tr><th scope="row">2024. 03.</th><td>간호학과 7명 증원(정원 167명)</td></tr>
<tr><th scope="row">2023. 08.</th><td>건양대 간호대학-헬스온클라우드(주) MOU</td></tr>
<tr><th scope="row">2023. 06.</th><td>4주기 간호교육인증평가 인증(한국간호교육평가원)</td></tr>
<tr><th scope="row">2022. 07.</th><td>제5대 학장 이미향 교수 취임</td></tr>
<tr><th scope="row">2022. 05.</th><td>건양대 간호대학-(주)디에이블 MOU</td></tr>
<tr><th scope="row">2022. 03.</th><td>건양대 간호대학-건양고등학교 MOU</td></tr>
<tr><th scope="row">2021. 04.</th><td>RN-BSN과정 잠정 중단</td></tr>
<tr><th scope="row">2021. 03.</th><td>일반대학원 외국인특별과정 개설(정원 18명)</td></tr>
<tr><th scope="row">2020. 07.</th><td>제4대 학장 이미향 교수 취임</td></tr>
<tr><th scope="row">2020. 03.</th><td>간호학과 10명 감원(정원 160명)</td></tr>
</tbody></table>
<h2 class="text-heading-md text-brand-60" style="margin-top:32px">2010''s</h2>
<table class="krds-table" style="margin-top:8px"><caption>2010년대 연혁</caption><tbody>
<tr><th scope="row" style="width:120px;white-space:nowrap">2018. 08.</th><td>제3대 학장 정선영 교수 취임</td></tr>
<tr><th scope="row">2018. 06.</th><td>3주기 간호교육인증평가 인증(한국간호교육평가원)</td></tr>
<tr><th scope="row">2018. 03.</th><td>간호학과 20명 증원(정원 170명)</td></tr>
<tr><th scope="row">2017. 08.</th><td>간호학관 신축 준공</td></tr>
<tr><th scope="row">2017. 07.</th><td>일반대학원 종양전문 간호과정 인가(정원 5명)</td></tr>
<tr><th scope="row">2017. 06.</th><td>2017 이공분야 대학중점연구소 지원사업 선정(교육부)</td></tr>
<tr><th scope="row">2017. 05.</th><td>건양대 간호대학-대전보건교사회 MOU</td></tr>
<tr><th scope="row">2017. 01.</th><td>제2대 학장 한수정 교수 취임</td></tr>
<tr><th scope="row">2016. 02.</th><td>간호학과 재학생 스탠포드 대학병원 임상실습 교류PG 운영</td></tr>
<tr><th scope="row">2014. 08.</th><td>제1대 학장 심문숙 교수 취임 · <strong>간호대학 승격</strong>(정원 150명)</td></tr>
<tr><th scope="row">2014. 03.</th><td>일반대학원 간호학 박사과정 인가</td></tr>
<tr><th scope="row">2013. 12.</th><td>2주기 간호교육인증평가 인증(한국간호교육평가원)</td></tr>
<tr><th scope="row">2012. 08.</th><td>제10대 학과장 양남영 교수 취임 · 간호학과 30명 증원(정원 150명)</td></tr>
<tr><th scope="row">2011. 08.</th><td>간호학과 30명 증원(정원 120명)</td></tr>
<tr><th scope="row">2010. 08.</th><td>제9대 학과장 문영숙 교수 취임 · 간호학과 20명 증원(정원 90명)</td></tr>
</tbody></table>
<h2 class="text-heading-md text-brand-60" style="margin-top:32px">2000''s</h2>
<table class="krds-table" style="margin-top:8px"><caption>2000년대 연혁</caption><tbody>
<tr><th scope="row" style="width:120px;white-space:nowrap">2009.</th><td>가정전문간호 전공 폐과 · 간호학과 20명 증원(정원 70명) · RN-BSN과정 평가 인증 · 감염관리전문간호전공 개설</td></tr>
<tr><th scope="row">2008.</th><td>감염관리 전문 간호전공 과정 인가 · 제8대 학과장 한수정 교수 취임 · 간호학과 10명 증원(정원 50명)</td></tr>
<tr><th scope="row">2006. 12.</th><td><strong>1주기 간호교육인증평가 인증</strong>(한국간호교육평가원) · 노인전문간호전공 과정 인가</td></tr>
<tr><th scope="row">2006. 03.</th><td>대전캠퍼스 의과학관으로 위치변경</td></tr>
<tr><th scope="row">2005.</th><td>가정전문간호전공 과정 인가 · 개교 10주년 기념 국제학술대회 개최</td></tr>
<tr><th scope="row">2004. 02.</th><td>제1회 간호학석사 배출</td></tr>
<tr><th scope="row">2001.</th><td>일반대학원 석사과정 인가 · RN-BSN 과정 10명 증원(정원 40명)</td></tr>
<tr><th scope="row">2000.</th><td>건양대학교병원(대전) 개원 · 제1회 RN-BSN 과정 입학(30명)</td></tr>
</tbody></table>
<h2 class="text-heading-md text-brand-60" style="margin-top:32px">1990''s</h2>
<table class="krds-table" style="margin-top:8px"><caption>1990년대 연혁</caption><tbody>
<tr><th scope="row" style="width:120px;white-space:nowrap">1999.</th><td>RN-BSN 과정 신설인가(정원 30명) · 제3대 학과장 심문숙 교수 취임 · <strong>제1회 간호학과 졸업생 배출(37명)</strong></td></tr>
<tr><th scope="row">1997. 02.</th><td>제2대 학과장 한진숙 교수 취임</td></tr>
<tr><th scope="row">1995. 03.</th><td>제1대 학과장 한진숙 교수 취임 · <strong>제1회 입학식(40명)</strong></td></tr>
<tr><th scope="row">1994. 09.</th><td><strong>간호학과 신설 인가</strong>(정원 40명)</td></tr>
</tbody></table>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000704';

-- ⑤ 교수소개 (faculty) — 원사이트는 인사소개 프로그램(prog) 연동 — 요약 + 안내로 구성
UPDATE tb_content SET
  summary = '전임교원 소개 — 상세 프로필은 교수소개 프로그램 연동 예정',
  body = '<h2 class="text-heading-md">교수소개</h2>
<p class="text-body-md" style="margin-top:12px">간호대학은 성인간호학·아동간호학·여성건강간호학·정신간호학·지역사회간호학·기본간호학 등
전공 영역별 전임교원이 이론과 임상실습 교육을 담당하고 있습니다.</p>
<div class="krds-alert krds-alert-info" role="status" style="margin-top:16px">
  <span aria-hidden="true">i</span>
  <p>교수별 상세 프로필(사진·연구분야·연락처)은 원사이트의 인사소개 프로그램과 연동되는
     교수소개 프로그램(/prg) 페이즈에서 제공됩니다.</p>
</div>
<div class="grid grid-cols-12 gap-4" style="margin-top:16px">
  <div class="col-span-6 md:col-span-3 krds-card text-center"><p class="text-heading-xl text-brand-60">15</p><p class="text-body-xs text-fg-subtle">전임 교원</p></div>
  <div class="col-span-6 md:col-span-3 krds-card text-center"><p class="text-heading-xl text-brand-60">6</p><p class="text-body-xs text-fg-subtle">전공 영역</p></div>
  <div class="col-span-6 md:col-span-3 krds-card text-center"><p class="text-heading-xl text-brand-60">1:8</p><p class="text-body-xs text-fg-subtle">실습 지도 비율</p></div>
  <div class="col-span-6 md:col-span-3 krds-card text-center"><p class="text-heading-xl text-brand-60">5년</p><p class="text-body-xs text-fg-subtle">간호교육 인증(2023)</p></div>
</div>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000705';

-- ⑥ 찾아오는길 (location) — 실측 주소·연락처, 지도는 P7 지도 API 슬롯
UPDATE tb_content SET
  summary = '메디컬캠퍼스 간호학관 1층 행정실 — 042-600-8551~4',
  body = '<h2 class="text-heading-md">찾아오는길</h2>
<div class="krds-card" style="margin-top:16px;min-height:280px;display:flex;align-items:center;justify-content:center;background:var(--c-surface-subtle)">
  <p class="text-body-sm text-fg-subtle">지도 영역 — 카카오맵 API 연동(P7 지도 위젯)</p>
</div>
<div class="grid grid-cols-12 gap-4" style="margin-top:16px">
  <div class="col-span-12 md:col-span-6 krds-card">
    <span class="krds-badge bg-brand-50 text-fg-on">메디컬캠퍼스</span>
    <p class="text-body-md" style="margin-top:12px">35365 대전광역시 서구 관저동로 158<br/>건양대학교 간호학관 1층 행정실</p>
  </div>
  <div class="col-span-12 md:col-span-6 krds-card">
    <table class="krds-table"><caption>연락처</caption><tbody>
      <tr><th scope="row" style="width:80px">전화</th><td>042-600-8551~4</td></tr>
      <tr><th scope="row">팩스</th><td>042-600-8555</td></tr>
      <tr><th scope="row">이메일</th><td>nurse@konyang.ac.kr</td></tr>
    </tbody></table>
  </div>
</div>'
WHERE content_id = 'CNT_01985a10-0000-7000-8000-000000000706';
