package com.gonet.primary.auth.controller;

import com.gonet.common.web.PageRequest;
import com.gonet.primary.auth.dto.RoleAdmDto;
import com.gonet.primary.auth.dto.RoleRebuildResult;
import com.gonet.primary.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 역할 관리 (/adm/role) — 계층과 권한 코드.
 *
 * <p>계층을 바꾸면 closure 와 계정 role_ids 스냅샷이 낡는다. 저장·삭제는 서비스가
 * 재전개까지 함께 하지만, 콘솔에서 SQL 로 손댄 경우를 위해 <b>수동 재전개 버튼</b>도 둔다
 * (배치는 기본 비활성 — PLAN P6-2).
 */
@Controller
@RequiredArgsConstructor
public class RoleAdmController {

    private static final String LIST = "redirect:/adm/role";

    private final RoleService roleService;

    @GetMapping("/adm/role")
    public String list(@ModelAttribute("cond") PageRequest cond, Model model) {
        model.addAttribute("page", roleService.getAdmPage(cond));
        return "adm/role/list";
    }

    @GetMapping("/adm/role/form")
    public String form(@RequestParam(required = false) String roleId, Model model) {
        RoleAdmDto role = roleId == null ? newRole() : roleService.getAdm(roleId);
        if (role == null) {
            return LIST;
        }
        model.addAttribute("role", role);
        model.addAttribute("roles", roleService.getAllForSelect());
        return "adm/role/form";
    }

    @PostMapping("/adm/role/save")
    public String save(@ModelAttribute("role") RoleAdmDto role, Model model,
            RedirectAttributes redirect) {
        try {
            roleService.saveAdm(role);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // IllegalState 는 RoleClosure 의 순환 참조 — 계층이 깨졌다는 뜻이라 같이 잡는다
            model.addAttribute("flashError", e.getMessage());
            model.addAttribute("roles", roleService.getAllForSelect());
            return "adm/role/form";
        }
        redirect.addFlashAttribute("flashOk", "저장했습니다. 계층을 다시 전개해 권한을 맞췄습니다.");
        return LIST;
    }

    @PostMapping("/adm/role/delete")
    public String delete(@RequestParam String roleId, RedirectAttributes redirect) {
        try {
            roleService.deleteAdm(roleId);
            redirect.addFlashAttribute("flashOk", "삭제했습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    /** 수동 재전개 — 콘솔 수기 수정 등으로 계층과 스냅샷이 어긋났을 때. */
    @PostMapping("/adm/role/rebuild")
    public String rebuild(RedirectAttributes redirect) {
        try {
            RoleRebuildResult result = roleService.rebuildHierarchy();
            redirect.addFlashAttribute("flashOk",
                    "재전개 완료 — 역할 %d · 계층 %d행 · 관리자 %d명 · 회원 %d명 교정."
                            .formatted(result.roles(), result.edges(),
                                    result.adminsFixed(), result.membersFixed()));
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return LIST;
    }

    private RoleAdmDto newRole() {
        RoleAdmDto role = new RoleAdmDto();
        role.setUseYn("Y");
        return role;
    }
}
