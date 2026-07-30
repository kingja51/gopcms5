package com.gonet.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * URL 네임스페이스 규칙 — 게시판 화면이 붙기 전에 <b>라우팅 규칙 자체</b>를 고정한다.
 *
 * <p>여기가 틀리면 증상이 "사이트가 안 열린다"·"엉뚱한 사이트가 열린다" 로 나타나
 * 원인이 라우팅이라는 것이 드러나지 않는다. 그래서 화면보다 먼저 못 박는다.
 */
class UrlNamespacesTest {

    @Nested
    @DisplayName("사이트코드 자리 — 컨텐츠는 첫째, 프로그램은 둘째")
    class SiteCodePosition {

        @Test
        void 컨텐츠_경로는_첫_세그먼트가_사이트코드다() {
            assertThat(UrlNamespaces.siteCodeSegment("/ai/index")).isEqualTo("ai");
            assertThat(UrlNamespaces.siteCodeSegment("/ai/about")).isEqualTo("ai");
            assertThat(UrlNamespaces.siteCodeSegment("/nursingcollege/sitemap"))
                    .isEqualTo("nursingcollege");
        }

        @Test
        void 프로그램_경로는_두번째_세그먼트가_사이트코드다() {
            // /bbs/{siteCode}/{bbsCode} — 자리 순서가 컨텐츠와 반대다(conventions §5.1)
            assertThat(UrlNamespaces.siteCodeSegment("/bbs/ai/notice")).isEqualTo("ai");
            assertThat(UrlNamespaces.siteCodeSegment("/prg/ai/professor")).isEqualTo("ai");
        }

        @Test
        void 프로그램_이름만_있으면_사이트코드가_없다() {
            // /bbs 만으로는 어느 사이트인지 알 수 없다 — 기본 사이트로 떨어져야 한다
            assertThat(UrlNamespaces.siteCodeSegment("/bbs")).isNull();
            assertThat(UrlNamespaces.siteCodeSegment("/bbs/")).isNull();
        }

        @Test
        void 빈_마디는_건너뛴다() {
            // 우회 시도가 다른 판정을 받으면 안 된다 — //bbs//ai/ 도 같은 결과여야 한다
            assertThat(UrlNamespaces.siteCodeSegment("//bbs//ai//notice")).isEqualTo("ai");
            assertThat(UrlNamespaces.siteCodeSegment("//ai//about")).isEqualTo("ai");
        }

        @Test
        void 루트는_사이트코드가_없다() {
            assertThat(UrlNamespaces.siteCodeSegment("/")).isNull();
            assertThat(UrlNamespaces.siteCodeSegment("")).isNull();
            assertThat(UrlNamespaces.siteCodeSegment(null)).isNull();
        }
    }

    @Nested
    @DisplayName("건너뛰는 자리 — 사이트 개념이 없는 경로")
    class Skip {

        @Test
        void 관리자_api_정적자원_파일은_해석하지_않는다() {
            assertThat(UrlNamespaces.isSkip("adm")).isTrue();
            assertThat(UrlNamespaces.isSkip("api")).isTrue();
            assertThat(UrlNamespaces.isSkip("css")).isTrue();
            assertThat(UrlNamespaces.isSkip("file")).isTrue();
        }

        @Test
        void 프로그램은_건너뛰지_않는다() {
            // 건너뛰면 사이트 컨텍스트가 서지 않아 3축(layout·template·theme)이 적용되지 않는다
            assertThat(UrlNamespaces.isSkip("bbs")).isFalse();
            assertThat(UrlNamespaces.isSkip("prg")).isFalse();
        }
    }

    @Nested
    @DisplayName("예약어 — 사이트코드·컨텐츠 slug 로 쓸 수 없는 값")
    class Reserved {

        @Test
        void 건너뛰는_자리와_프로그램_이름은_모두_예약이다() {
            // 같은 자리를 다투므로 site_code 로 쓸 수 없다.
            // bbs 를 빠뜨리면 /bbs/… 가 게시판인지 그 사이트인지 구분되지 않는다.
            assertThat(UrlNamespaces.isReserved("adm")).isTrue();
            assertThat(UrlNamespaces.isReserved("file")).isTrue();
            assertThat(UrlNamespaces.isReserved("bbs")).isTrue();
            assertThat(UrlNamespaces.isReserved("prg")).isTrue();
        }

        @Test
        void 사이트_안의_고정_라우트도_예약이다() {
            assertThat(UrlNamespaces.isReserved("index")).isTrue();
            assertThat(UrlNamespaces.isReserved("sitemap")).isTrue();
            assertThat(UrlNamespaces.isReserved("search")).isTrue();
            assertThat(UrlNamespaces.isReserved("member")).isTrue();
        }

        @Test
        void 실제_사이트코드는_예약이_아니다() {
            assertThat(UrlNamespaces.isReserved("ai")).isFalse();
            assertThat(UrlNamespaces.isReserved("nursingcollege")).isFalse();
        }

        @Test
        void 예약_목록은_건너뛰기와_프로그램을_모두_포함한다() {
            assertThat(UrlNamespaces.RESERVED).containsAll(UrlNamespaces.SKIP);
            assertThat(UrlNamespaces.RESERVED).containsAll(UrlNamespaces.PROGRAM);
        }
    }

    /**
     * 표기 검증 — 신뢰할 수 없는 입력(요청 파라미터)을 URL 로 되돌려 보내기 전의 관문.
     *
     * <p>로그인 실패 리다이렉트가 {@code /login?error&siteCode=…} 를 조립하는데, 그 값이
     * 요청 파라미터다. 검증 없이 이어 붙이면 쿼리를 갈라 뒤에 임의 파라미터를 얹을 수 있다.
     * 정규식은 V1 의 {@code chk_site_code_pattern} 과 같아야 한다.
     */
    @Nested
    @DisplayName("사이트코드 표기 — DB CHECK 와 같은 규칙")
    class SiteCodeFormat {

        @Test
        void 소문자_숫자_하이픈_2자_이상만_통과한다() {
            assertThat(UrlNamespaces.isValidSiteCode("ai")).isTrue();
            assertThat(UrlNamespaces.isValidSiteCode("nursingcollege")).isTrue();
            assertThat(UrlNamespaces.isValidSiteCode("site-01")).isTrue();
        }

        @Test
        void 쿼리를_가르는_문자는_거른다() {
            // 이것이 통과하면 /login?error&siteCode=ai&role=admin 처럼 뒤를 이어 붙일 수 있다
            assertThat(UrlNamespaces.isValidSiteCode("ai&role=admin")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("ai#frag")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("ai ")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("ai/../adm")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("ai\nSet-Cookie: x")).isFalse();
        }

        @Test
        void 대문자_밑줄_한글은_사이트코드가_아니다() {
            assertThat(UrlNamespaces.isValidSiteCode("AI")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("ai_01")).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("사이트")).isFalse();
        }

        @Test
        void 길이_경계와_시작문자() {
            assertThat(UrlNamespaces.isValidSiteCode("a")).isFalse();          // 최소 2자
            assertThat(UrlNamespaces.isValidSiteCode("-ai")).isFalse();        // 하이픈 시작 불가
            assertThat(UrlNamespaces.isValidSiteCode("a".repeat(30))).isTrue();
            assertThat(UrlNamespaces.isValidSiteCode("a".repeat(31))).isFalse();
        }

        @Test
        void 빈값과_null_은_통과하지_않는다() {
            assertThat(UrlNamespaces.isValidSiteCode(null)).isFalse();
            assertThat(UrlNamespaces.isValidSiteCode("")).isFalse();
        }
    }
}
