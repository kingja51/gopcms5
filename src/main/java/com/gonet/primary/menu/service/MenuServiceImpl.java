package com.gonet.primary.menu.service;

import com.gonet.common.audit.AuditorContext;
import com.gonet.common.service.AbstractCmsService;
import com.gonet.common.util.LikeQuery;
import com.gonet.common.util.Uid;
import com.gonet.common.util.UidPrefix;
import com.gonet.config.CacheConfig;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.menu.dto.MenuAdmDto;
import com.gonet.primary.menu.dto.MenuNode;
import com.gonet.primary.menu.mapper.MenuMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 트리 조립 + href 해석 — 평면 목록(depth·sort 정렬)을 1회 순회로 트리화. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class MenuServiceImpl extends AbstractCmsService implements MenuService {

    /** 트리 최대 깊이 — 사이트맵 렌더가 4뎁스까지를 계약으로 잡고 있다(P3 _default/sitemap). */
    private static final int MAX_DEPTH = 4;

    private final MenuMapper menuMapper;

    @Override
    public List<MenuNode> getMenuTree(String siteId, String siteCode) {
        List<MenuNode> rows = menuMapper.findActiveBySiteId(siteId);
        Map<String, MenuNode> byId = new LinkedHashMap<>();
        List<MenuNode> roots = new ArrayList<>();

        for (MenuNode node : rows) {
            node.setHref(resolveHref(node, siteCode));
            byId.put(node.getMenuId(), node);
        }
        for (MenuNode node : rows) {
            MenuNode parent = node.getParentMenuId() == null
                    ? null : byId.get(node.getParentMenuId());
            if (parent == null) {
                // 정상 케이스는 depth=1. 부모가 비활성/삭제된 고아 노드도 방어적으로 루트 노출
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public List<MenuNode> findPathByMenuId(List<MenuNode> tree, String menuId) {
        List<MenuNode> path = new ArrayList<>();
        return dfs(tree, path, node -> node.getMenuId().equals(menuId)) ? path : List.of();
    }

    @Override
    public List<MenuNode> findPathByHref(List<MenuNode> tree, String href) {
        List<MenuNode> path = new ArrayList<>();
        return dfs(tree, path, node -> href != null && href.equals(node.getHref()))
                ? path : List.of();
    }

    /* ── 관리 CRUD (P7) ─────────────────────────────────────────────────── */

    @Override
    public List<MenuAdmDto> getAdmList(String siteId, String keyword) {
        // 메뉴는 페이징하지 않아 PageRequest 를 타지 않는다 — 이스케이프를 여기서 건다
        // (LIKE 검색은 어디서든 같은 규칙이어야 한다)
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return menuMapper.findAdmBySite(siteId, trimmed, LikeQuery.escape(trimmed));
    }

    @Override
    public MenuAdmDto getAdm(String menuId) {
        return menuMapper.findAdmById(menuId);
    }

    /** 쓰기 — writable override. 메뉴 트리는 siteContext 캐시에 실려 있어 함께 비운다. */
    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public String saveAdm(MenuAdmDto menu) {
        if (menu.getSiteId() == null || menu.getSiteId().isBlank()) {
            throw new IllegalArgumentException("사이트는 필수입니다.");
        }
        if (menu.getMenuName() == null || menu.getMenuName().isBlank()) {
            throw new IllegalArgumentException("메뉴 이름은 필수입니다.");
        }
        normalizeType(menu);
        menu.setDepth(resolveDepth(menu));

        if (menu.getMenuId() == null || menu.getMenuId().isBlank()) {
            menu.setMenuId(Uid.next(UidPrefix.MNU));
            menuMapper.insert(menu);
        } else {
            menuMapper.update(menu);
        }
        return menu.getMenuId();
    }

    @Override
    @Transactional(transactionManager = MyBatisConfig.PRIMARY_TX)
    @CacheEvict(cacheNames = CacheConfig.SITE_CONTEXT, allEntries = true)
    public void deleteAdm(String menuId) {
        int children = menuMapper.countChildren(menuId);
        if (children > 0) {
            // 부모만 지우면 자식이 루트로 튀어올라 GNB 가 뒤틀린다 — 아래부터 지우게 한다
            throw new IllegalArgumentException(
                    "하위 메뉴 " + children + "개를 먼저 삭제해야 합니다.");
        }
        menuMapper.softDelete(menuId,
                AuditorContext.currentUserId(), AuditorContext.currentIp());
    }

    /** menu_type 에 맞지 않는 링크 필드를 비운다 — 남은 값이 나중에 오해를 만든다. */
    private void normalizeType(MenuAdmDto menu) {
        String type = menu.getMenuType() == null ? "FOLDER" : menu.getMenuType();
        menu.setMenuType(type);
        switch (type) {
            case "CONTENT" -> {
                if (menu.getLinkTargetId() == null || menu.getLinkTargetId().isBlank()) {
                    throw new IllegalArgumentException("컨텐츠 메뉴는 연결할 컨텐츠를 골라야 합니다.");
                }
                menu.setLinkUrl(null);
            }
            case "URL" -> {
                if (menu.getLinkUrl() == null || menu.getLinkUrl().isBlank()) {
                    throw new IllegalArgumentException("URL 메뉴는 링크 주소가 필요합니다.");
                }
                menu.setLinkTargetId(null);
            }
            default -> { // FOLDER · BOARD(게시판 페이즈)
                menu.setLinkTargetId(null);
                menu.setLinkUrl(null);
            }
        }
    }

    /**
     * depth 는 상위 메뉴에서 파생된다 — 입력값을 믿지 않는다.
     * 자기 자신·자기 후손을 부모로 지정하면 트리가 끊어지므로 함께 막는다.
     */
    private int resolveDepth(MenuAdmDto menu) {
        String parentId = menu.getParentMenuId();
        if (parentId == null || parentId.isBlank()) {
            menu.setParentMenuId(null);
            return 1;
        }
        if (parentId.equals(menu.getMenuId())) {
            throw new IllegalArgumentException("자기 자신을 상위 메뉴로 지정할 수 없습니다.");
        }
        MenuAdmDto parent = menuMapper.findAdmById(parentId);
        if (parent == null) {
            throw new IllegalArgumentException("상위 메뉴를 찾을 수 없습니다.");
        }
        if (menu.getMenuId() != null && isDescendant(parent, menu.getMenuId())) {
            throw new IllegalArgumentException("하위 메뉴를 자신의 상위로 지정할 수 없습니다(순환).");
        }
        int depth = parent.getDepth() + 1;
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "메뉴는 " + MAX_DEPTH + "단계까지만 만들 수 있습니다(사이트맵 렌더 계약).");
        }
        return depth;
    }

    /** candidate 의 조상 사슬에 menuId 가 있는가 — 순환 지정 차단. */
    private boolean isDescendant(MenuAdmDto candidate, String menuId) {
        MenuAdmDto cursor = candidate;
        for (int guard = 0; cursor != null && guard <= MAX_DEPTH; guard++) {
            if (menuId.equals(cursor.getMenuId())) {
                return true;
            }
            cursor = cursor.getParentMenuId() == null
                    ? null : menuMapper.findAdmById(cursor.getParentMenuId());
        }
        return false;
    }

    /* menu_type 별 링크 해석 — MenuNode javadoc 계약 */
    private String resolveHref(MenuNode node, String siteCode) {
        return switch (node.getMenuType()) {
            case "CONTENT" -> node.getContentSlug() != null
                    ? "/" + siteCode + "/" + node.getContentSlug() : null;
            case "URL" -> node.getLinkUrl();
            case "BOARD" -> null; // 게시판 페이즈(V4)에서 /bbs/{sc}/{bbsCode} 연결
            default -> null;      // FOLDER
        };
    }

    private boolean dfs(List<MenuNode> nodes, List<MenuNode> path,
            java.util.function.Predicate<MenuNode> match) {
        for (MenuNode node : nodes) {
            path.add(node);
            if (match.test(node) || dfs(node.getChildren(), path, match)) {
                return true;
            }
            path.remove(path.size() - 1);
        }
        return false;
    }
}
