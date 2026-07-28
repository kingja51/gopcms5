package com.gonet.primary.menu.service;

import com.gonet.common.service.AbstractCmsService;
import com.gonet.config.datasource.MyBatisConfig;
import com.gonet.primary.menu.dto.MenuNode;
import com.gonet.primary.menu.mapper.MenuMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 메뉴 트리 조립 + href 해석 — 평면 목록(depth·sort 정렬)을 1회 순회로 트리화. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, transactionManager = MyBatisConfig.PRIMARY_TX)
public class MenuServiceImpl extends AbstractCmsService implements MenuService {

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
