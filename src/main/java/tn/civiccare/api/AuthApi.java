package tn.civiccare.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUserRepository;

import java.util.List;
import java.util.Map;

/**
 * Session courante pour la SPA. La connexion (POST /api/v1/auth/login, formulaire
 * username/password) et la déconnexion (POST /api/v1/auth/logout) sont gérées par
 * Spring Security (voir SecurityConfig) ; cookies de session HttpOnly conservés.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthApi {

    private final StaffUserRepository staffUsers;

    public AuthApi(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    /** 200 toujours : {authenticated:false} pour un anonyme (pas un 401, appel de bootstrap). */
    @GetMapping("/me")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return Map.of("authenticated", false);
        }
        return staffUsers.findByUsername(auth.getName())
                .<Map<String, Object>>map(user -> Map.of(
                        "authenticated", true,
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "roles", user.getRoles().stream().map(Enum::name).toList(),
                        "departments", user.getDepartments().stream()
                                .map(d -> Map.of("id", d.getId(), "code", d.getCode(),
                                        "nameFr", d.getNameFr()))
                                .toList()))
                .orElse(Map.of("authenticated", false));
    }
}
