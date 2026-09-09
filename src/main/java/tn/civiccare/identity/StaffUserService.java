package tn.civiccare.identity;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.observability.Telemetry.ValidationException;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gestion des comptes internes. Réservée à ADMIN ; aucune inscription publique. */
@Service
public class StaffUserService {

    private final StaffUserRepository users;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public StaffUserService(StaffUserRepository users, DepartmentRepository departments,
                            PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.departments = departments;
        this.encoder = encoder;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<StaffUser> allUsers() {
        List<StaffUser> list = users.findAll();
        list.forEach(u -> {
            u.getRoles().size();
            u.getDepartments().size();
        });
        return list;
    }

    @Transactional(readOnly = true)
    public List<Department> allDepartments() {
        return departments.findAll();
    }

    @Transactional(readOnly = true)
    public List<StaffUser> agentsOfDepartment(UUID departmentId) {
        return allUsers().stream()
                .filter(StaffUser::isEnabled)
                .filter(u -> u.getRoles().contains(Role.ADMIN)
                        || u.getDepartments().stream().anyMatch(d -> d.getId().equals(departmentId)))
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public StaffUser create(String username, String password, String displayName,
                            Set<Role> roles, Set<UUID> departmentIds) {
        if (username == null || username.isBlank() || password == null || password.length() < 10) {
            throw new ValidationException("user.invalid");
        }
        if (users.findByUsername(username.trim()).isPresent()) {
            throw new ValidationException("user.exists");
        }
        StaffUser user = new StaffUser(UUID.randomUUID(), username.trim(),
                encoder.encode(password), displayName == null ? username : displayName, clock.instant());
        user.getRoles().addAll(roles);
        departmentIds.forEach(id -> departments.findById(id).ifPresent(user.getDepartments()::add));
        return users.save(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        users.findById(userId).ifPresent(u -> u.setEnabled(enabled));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void updateRolesAndDepartments(UUID userId, Set<Role> roles, Set<UUID> departmentIds) {
        users.findById(userId).ifPresent(u -> {
            u.getRoles().clear();
            u.getRoles().addAll(roles);
            u.getDepartments().clear();
            departmentIds.forEach(id -> departments.findById(id).ifPresent(u.getDepartments()::add));
        });
    }
}
