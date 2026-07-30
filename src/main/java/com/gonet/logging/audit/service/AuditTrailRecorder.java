package com.gonet.logging.audit.service;

import com.gonet.common.util.Uid;
import com.gonet.common.web.ClientIpResolver;
import com.gonet.common.web.LoginPrincipal;
import com.gonet.logging.audit.dto.AuditLog;
import com.gonet.logging.error.service.ErrorLogger;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 요청 한 건을 감사 로그로 바꾼다 — <b>관리자 CUD 전수 기록의 단일 창구</b>.
 *
 * <p>행위·대상은 URL 에서 <b>기계적으로</b> 끌어낸다. 도메인마다 호출을 심지 않는 이유는
 * "전수" 를 코드 규율에 맡기지 않기 위해서다 — 새 관리자 화면을 만들 때 한 줄을 빠뜨리면
 * 그 화면만 조용히 감사에서 빠지고, 빠진 사실은 아무도 모른다.
 *
 * <h3>URL 해석 규칙</h3>
 * 이 프로젝트의 관리자 URL 은 형태가 일정해서 규칙이 성립한다(conventions §4·§7):
 * <pre>
 *   /adm/{entity}/save                    → entity, CREATE 또는 UPDATE
 *   /adm/{entity}/delete                  → entity, DELETE
 *   /adm/{entity}/{ID}/{action}           → entity, ACTION, targetId=ID
 *   /adm/{entity}/{sub}/{action}          → entity, ACTION
 *   /adm/{entity}                         → entity, UPDATE
 * </pre>
 * {@code save} 의 CREATE/UPDATE 구분은 <b>대상 ID 가 실려 왔는지</b>로 한다 —
 * 이 프로젝트의 등록 폼은 ID 를 비워 보내고 수정 폼은 채워 보낸다.
 *
 * <h3>기록하지 않는 것</h3>
 * <ul>
 *   <li><b>요청 본문</b> — 비밀번호·임시 비밀번호가 섞여 있다. 파라미터 중
 *       식별자만 골라 읽고 나머지는 보지 않는다</li>
 *   <li><b>쿼리스트링</b> — 마스킹 해제 사유({@code ?reason=}) 등이 실린다.
 *       그 기록은 {@code log_privacy_access} 가 사유 컬럼으로 이미 갖고 있다</li>
 *   <li><b>변경 전/후 값</b> — 인터셉터는 요청이 끝난 뒤에 돈다([AuditLog#beforeJson])</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrailRecorder {

    /** 경로·파라미터에서 대상 ID 를 알아보는 기준 — PK 규약(conventions §1)과 같은 식이다. */
    private static final Pattern ID = Pattern.compile(Uid.PATTERN);

    /**
     * 엔티티별 대상 ID 파라미터 — <b>엔티티마다 이름이 하나로 정해진다</b>.
     *
     * <p>처음에는 "알려진 ID 파라미터 목록" 을 순서대로 훑었는데 그것이 틀렸다(실측
     * 2026-07-30). 게시판 <b>신규</b> 등록은 {@code bbsMasterId} 가 비어 있고
     * {@code siteId} 만 실려 오는데, 목록 방식은 {@code siteId} 를 대상으로 집어
     * <b>사이트를 고친 것처럼</b> 기록했고 ID 가 잡혔으니 {@code CREATE} 도
     * {@code UPDATE} 로 기록했다. {@code siteId}·{@code fileGroupId} 는 대상이 아니라
     * <b>상위 참조</b>라서 대상 후보가 될 수 없다.
     *
     * <p>요청 본문 전체를 훑지 않는 이유는 그대로다 — 비밀번호까지 읽게 된다.
     * 새 도메인을 열면 여기에 한 줄을 더한다. 빠뜨려도 감사 기록 자체는 남고
     * {@code target_id} 만 비므로 안전한 방향으로 실패한다.
     */
    private static final Map<String, String> TARGET_ID_PARAM = Map.ofEntries(
            Map.entry("BOARD", "bbsMasterId"),
            Map.entry("BOARD_ARTICLE", "articleId"),
            Map.entry("BOARD_ARTICLE_COMMENT", "commentId"),
            Map.entry("BOARD_CATEGORY", "categoryId"),
            Map.entry("BOARD_REPORT", "reportId"),
            Map.entry("MEMBER", "memberId"),
            Map.entry("CONTENT", "contentId"),
            Map.entry("MENU", "menuId"),
            Map.entry("SITE", "siteId"),
            Map.entry("ROLE", "roleId"),
            Map.entry("URL_ACCESS", "urlAccessId"),
            Map.entry("THEME", "themeId"),
            Map.entry("TEMPLATE", "templateId"),
            Map.entry("LAYOUT", "layoutId"),
            Map.entry("FILE", "fileId"));

    /** 엔티티 이름에 넣지 않는 마디 — 행위 이름이다. */
    private static final Set<String> ACTION_SEGMENTS = Set.of(
            "save", "delete", "new", "status", "unlock", "password", "withdraw",
            "restore", "evict", "rebuild", "review", "moderate", "setup",
            "scan-status", "download-auth", "batch");

    /**
     * PK 를 <b>폼 열 때 미리 발급</b>하는 엔티티 — CREATE/UPDATE 를 요청만 보고 가릴 수 없다.
     *
     * <p>게시글은 첨부 picker 가 저장 전에 파일을 올려야 해서 {@code /write} 화면이
     * {@code articleId} 를 미리 만든다(doc/board-domain.md · 게시글 생명주기 §1).
     * 그래서 <b>신규 글도 ID 를 달고 들어온다</b> — 서비스조차 DB 를 조회해서 판정한다
     * ({@code resolveNew}). 감사 기록이 그것을 흉내내려고 조회를 하면 감사가 도메인
     * 로직을 복제하게 되므로, 여기서는 <b>모른다고 적는다</b>: {@code SAVE}.
     *
     * <p>실측(2026-07-30): 이 목록이 없을 때 신규 게시글이 {@code UPDATE} 로 기록됐다.
     */
    private static final Set<String> PREISSUED_PK_ENTITIES = Set.of("BOARD_ARTICLE");

    /** 행위 이름이 URL 마지막 마디와 다른 자리. */
    private static final Map<String, String> ACTION_ALIAS = Map.of(
            "save", "SAVE",          // ID 유무로 CREATE/UPDATE 로 다시 갈린다
            "delete", "DELETE",
            "new", "CREATE");

    private static final int URI_MAX = 1000;
    private static final int UA_MAX = 500;

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String RESULT_ERROR = "ERROR";

    private final AuditLogService auditLogService;
    private final ClientIpResolver clientIpResolver;
    private final ErrorLogger errorLogger;

    /**
     * 상태를 바꾼 관리자 요청 한 건을 기록한다.
     *
     * <p><b>절대 예외를 던지지 않는다.</b> 감사 로그 때문에 관리자 업무가 멈추면 손해가
     * 더 크다. 대신 실패를 드러낸다 — {@code log_error} 에
     * {@code RECORD_FAILURE:AUDIT_LOG} 로 남아 관리자 화면에서 보인다.
     *
     * @param statusCode 응답 상태 코드
     * @param failed     처리 실패로 볼 근거가 있는가(예외 발생 · flashError 존재)
     */
    public void record(HttpServletRequest request, int statusCode, boolean failed) {
        try {
            auditLogService.write(build(request, statusCode, failed));
        } catch (RuntimeException e) {
            log.warn("감사 로그 적재 실패(업무는 계속) uri={} status={}: {}",
                    request.getRequestURI(), statusCode, e.toString());
            errorLogger.logRecordFailure("AUDIT_LOG",
                    "%s %s status=%d".formatted(request.getMethod(),
                            request.getRequestURI(), statusCode), e);
        } catch (Throwable t) {
            // 감사 기록이 프로세스를 끌고 내려가면 안 된다
            log.error("감사 로그 적재 중 치명적 오류 uri={}", request.getRequestURI(), t);
        }
    }

    private AuditLog build(HttpServletRequest request, int statusCode, boolean failed) {
        String[] segments = segments(request.getRequestURI());
        String entity = resolveEntity(segments);
        String targetId = resolveTargetId(request, segments, entity);

        AuditLog.AuditLogBuilder row = AuditLog.builder()
                .action(resolveAction(segments, targetId, entity))
                .targetEntity(entity)
                .targetId(targetId)
                .requestUri(trim(request.getRequestURI(), URI_MAX))
                .httpMethod(request.getMethod())
                .clientIp(clientIpResolver.resolve(request))
                .userAgent(trim(request.getHeader("User-Agent"), UA_MAX))
                .result(resolveResult(statusCode, failed))
                .traceId(MDC.get("traceId"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginPrincipal principal) {
            row.actorUserId(principal.userId())
                    .actorUserType(principal.userType())
                    .actorLoginId(principal.loginId())
                    .createdBy(principal.userId());
        } else {
            // /adm/** 는 인증 뒤에만 열리므로 정상 경로에서는 오지 않는다.
            // 그래도 남긴다 — "주체를 못 밝힌 변경" 이 있었다는 사실 자체가 신호다.
            row.actorUserType("UNKNOWN");
        }
        return row.createdIp(clientIpResolver.resolve(request)).build();
    }

    /** {@code /adm/board/BBM_…/article/save} → [adm, board, BBM_…, article, save] */
    private String[] segments(String uri) {
        return uri == null ? new String[0] : uri.replaceAll("^/+|/+$", "").split("/+");
    }

    /**
     * 대상 엔티티 — {@code adm} · ID 마디 · 행위 마디를 걷어낸 <b>나머지를 이어 붙인다</b>.
     *
     * <pre>
     *   /adm/board/save                                  → BOARD
     *   /adm/url-access/evict                            → URL_ACCESS
     *   /adm/board/category/save                         → BOARD_CATEGORY
     *   /adm/board/BBM_…/article/save                    → BOARD_ARTICLE
     *   /adm/board/BBM_…/article/comment/moderate        → BOARD_ARTICLE_COMMENT
     *   /adm/member/MBR_…/status                         → MEMBER
     *   /adm/2fa/setup                                   → 2FA
     * </pre>
     *
     * <p>두 번째 마디만 쓰면 중첩 자원이 전부 부모 이름으로 뭉개진다 — 게시글·댓글·
     * 카테고리 변경이 모두 {@code BOARD} 가 되어 감사에서 구분되지 않는다.
     *
     * <p>하이픈은 밑줄로 바꾼다({@code url-access} → {@code URL_ACCESS}) — 컬럼 값으로
     * 필터에 쓰이므로 표기가 흔들리면 집계가 갈린다.
     */
    private String resolveEntity(String[] segments) {
        StringBuilder entity = new StringBuilder();
        for (int i = 1; i < segments.length; i++) {           // 0 = "adm"
            String segment = segments[i];
            if (ID.matcher(segment).matches() || ACTION_SEGMENTS.contains(segment)) {
                continue;
            }
            // batch/{job}·2fa/setup 처럼 행위 뒤에 오는 마디는 이름에 넣지 않는다
            if (i > 1 && ACTION_SEGMENTS.contains(segments[i - 1])) {
                continue;
            }
            if (!entity.isEmpty()) {
                entity.append('_');
            }
            entity.append(normalize(segment));
        }
        // 전부 걸러졌으면 두 번째 마디가 곧 엔티티다 — /adm/password 처럼 엔티티 이름과
        // 행위 단어가 같은 자리. 예전에는 여기서 null 을 돌려줘 아래 Map.get(null) 이
        // NPE 를 냈다(Map.ofEntries·Set.of 는 null 키에 NPE 를 던진다. 실측 2026-07-30 —
        // log_error 의 RECORD_FAILURE:AUDIT_LOG 로 드러났다).
        if (entity.isEmpty()) {
            return segments.length >= 2 ? normalize(segments[1]) : null;
        }
        return entity.toString();
    }

    /**
     * 행위 — URL 마지막 마디. {@code save} 는 ID 유무로 CREATE/UPDATE 로 갈린다.
     *
     * <p>마지막 마디가 엔티티 마디와 같으면(예: {@code /adm/password}) 값 변경 화면이므로
     * {@code UPDATE} 로 본다.
     *
     * <p>배치는 {@code BATCH_{job}} 으로 적는다({@code /adm/member/batch/notice} →
     * {@code BATCH_NOTICE}) — 마지막 마디만 쓰면 {@code NOTICE} 가 되어 무슨 행위인지
     * 읽히지 않는다.
     */
    private String resolveAction(String[] segments, String targetId, String entity) {
        if (segments.length < 2) {
            return "UNKNOWN";
        }
        if (segments.length == 2) {
            return "UPDATE";
        }
        String last = segments[segments.length - 1];
        // 마지막 마디가 ID 면 행위 이름이 아니다 — 그 앞 마디를 본다
        if (ID.matcher(last).matches() && segments.length >= 3) {
            last = segments[segments.length - 2];
        }
        // batch/{job}·2fa/setup 처럼 행위 마디 뒤에 세부 이름이 오는 형태
        String previous = segments[segments.length - 2];
        if (!last.equals(previous) && ACTION_SEGMENTS.contains(previous)
                && !ACTION_SEGMENTS.contains(last)) {
            return normalize(previous) + "_" + normalize(last);
        }
        String action = ACTION_ALIAS.getOrDefault(last, normalize(last));
        if ("SAVE".equals(action)) {
            // PK 선발급 도메인은 요청만으로 신규/수정을 가릴 수 없다 — SAVE 로 남긴다
            if (entity != null && PREISSUED_PK_ENTITIES.contains(entity)) {
                return "SAVE";
            }
            return targetId == null ? "CREATE" : "UPDATE";
        }
        return action;
    }

    /**
     * 대상 ID — <b>엔티티가 정한 파라미터를 먼저</b>, 없으면 경로의 ID 마디.
     *
     * <p>순서가 중요하다. {@code /adm/board/{bbsMasterId}/article/save} 는 경로에
     * <b>게시판</b> ID 가 있고 파라미터에 <b>게시글</b> ID 가 있다 — 감사의 대상은
     * 게시글이므로 엔티티({@code BOARD_ARTICLE})가 지정한 {@code articleId} 가 이겨야 한다.
     * 반대로 {@code /adm/member/{memberId}/status} 는 파라미터가 없어 경로가 답이다.
     *
     * <p>둘 다 없으면 NULL 이다 — 신규 등록이 그렇다. 그 NULL 이
     * {@link #resolveAction} 에서 CREATE/UPDATE 를 가르는 근거가 된다.
     */
    private String resolveTargetId(HttpServletRequest request, String[] segments, String entity) {
        // 불변 컬렉션은 null 키 조회에도 NPE 를 던진다 — 방어적으로 먼저 걸러낸다
        String param = entity == null ? null : TARGET_ID_PARAM.get(entity);
        if (param != null) {
            String value = request.getParameter(param);
            if (value != null && !value.isBlank() && ID.matcher(value.trim()).matches()) {
                return value.trim();
            }
        }
        for (int i = segments.length - 1; i >= 0; i--) {
            if (ID.matcher(segments[i]).matches()) {
                return segments[i];
            }
        }
        return null;
    }

    /**
     * 결과 판정 — SUCCESS | FAIL | ERROR.
     *
     * <p>{@code statusCode} 만으로는 판정할 수 없다. {@link #looksFailed} 가 넘겨 주는
     * 근거를 함께 본다.
     */
    private String resolveResult(int statusCode, boolean failed) {
        if (statusCode >= 500) {
            return RESULT_ERROR;
        }
        if (failed || statusCode >= 400) {
            return RESULT_FAIL;
        }
        return RESULT_SUCCESS;
    }

    /**
     * 처리 실패로 볼 근거가 있는가.
     *
     * <p>세 가지를 본다:
     * <ol>
     *   <li>예외가 올라왔다</li>
     *   <li>{@code flashError} 플래시 속성이 있다 — 리다이렉트로 되돌리는 실패</li>
     *   <li><b>2xx 응답이다</b> — 아래 설명</li>
     * </ol>
     *
     * <h3>왜 2xx 가 실패 신호인가</h3>
     * {@code /adm/**} 의 쓰기 핸들러는 <b>성공하면 예외 없이 리다이렉트</b>한다(PRG).
     * 값 검증에 걸리면 {@code model.addAttribute("flashError", …)} 후 <b>폼 뷰를 다시
     * 그려 200</b> 을 낸다. 그 오류는 <b>Model</b> 에 담기고 플래시 맵에는 없어서
     * ②로는 잡히지 않는다 — 실측(2026-07-30)에서 게시판 코드 중복 저장이
     * {@code SUCCESS} 로 기록된 원인이 이것이다.
     *
     * <p>그래서 "쓰기 요청 + 200" 을 실패로 본다. 이 규칙은 <b>인터셉터가
     * {@code /adm/**} 에만 걸려 있어서</b> 성립한다 — 관리자 쓰기 엔드포인트는 전부
     * 뷰/리다이렉트를 반환하고 200 본문으로 성공을 알리는 자리가 없다.
     * {@code /api/**} 처럼 200 이 곧 성공인 영역에 인터셉터를 확장하려면
     * <b>이 판정을 먼저 고쳐야 한다</b>.
     *
     * <h3>남는 한계 두 가지</h3>
     * <ul>
     *   <li><b>{@code flashMessage} 로 실패를 알리는 화면</b>은 SUCCESS 로 기록된다.
     *       그 키는 성공·실패에 모두 쓰여(파일 관리 화면 등) 실패 신호로 쓸 수 없다.
     *       실측: {@code POST /adm/file/new} 에 잘못된 entityType 을 보내면 거부되지만
     *       리다이렉트 + flashMessage 라 SUCCESS 로 남는다</li>
     *   <li><b>인가·CSRF 로 막힌 요청은 아예 기록되지 않는다</b> — 시큐리티 필터가
     *       DispatcherServlet 앞에서 끊어 인터셉터가 돌지 않는다. 그 시도는
     *       {@code log_access} 의 상태코드(403)로 남는다</li>
     * </ul>
     */
    public boolean looksFailed(HttpServletRequest request, Exception ex) {
        if (ex != null) {
            return true;
        }
        try {
            Map<String, ?> flash = RequestContextUtils.getOutputFlashMap(request);
            if (flash != null && flash.containsKey("flashError")) {
                return true;
            }
        } catch (RuntimeException e) {
            // 플래시 맵이 없는 요청 — 다른 근거로 판단한다
        }
        return false;
    }

    /**
     * 리다이렉트하지 않은 쓰기 응답인가 — 폼 재표시(검증 실패)를 뜻한다.
     *
     * <p>판정 근거는 {@link #looksFailed} javadoc 참조. 인터셉터가 상태 코드를 알고
     * 있으므로 그쪽에서 합쳐 넘긴다.
     */
    public boolean isFormRedisplay(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private String normalize(String value) {
        return value == null ? null : value.replace('-', '_').toUpperCase();
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
