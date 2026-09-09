package tn.civiccare.administration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.identity.Role;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserRepository;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.reports.WorkflowStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requêtes du back-office. Le périmètre des données est appliqué ICI, au niveau service :
 * un AGENT ne voit que les dossiers de ses équipes ; MODERATOR et ADMIN voient tout.
 */
@Service
public class AdminFacade {

    @PersistenceContext
    private EntityManager em;

    private final StaffUserRepository staffUsers;

    public AdminFacade(StaffUserRepository staffUsers) {
        this.staffUsers = staffUsers;
    }

    public record WorkQueueFilter(WorkflowStatus status, PublicationStatus publication,
                                  UUID departmentId, boolean onlyMine, boolean onlyUnassigned) {
        public static WorkQueueFilter empty() {
            return new WorkQueueFilter(null, null, null, false, false);
        }
    }

    public record WorkQueueRow(UUID id, String reference, String typeLabelFr, WorkflowStatus status,
                               PublicationStatus publication, String departmentName, String assigneeName,
                               Instant createdAt, double longitude, double latitude) {
    }

    @Transactional(readOnly = true)
    public StaffUser requireUser(String username) {
        return staffUsers.findByUsername(username).map(u -> {
            u.getDepartments().size();
            u.getRoles().size();
            return u;
        }).orElseThrow(() -> new IllegalStateException("Utilisateur interne inconnu"));
    }

    private boolean seesAll(StaffUser user) {
        return user.getRoles().contains(Role.ADMIN) || user.getRoles().contains(Role.MODERATOR);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<WorkQueueRow> workQueue(String username, WorkQueueFilter filter, int offset, int limit) {
        StaffUser user = requireUser(username);
        StringBuilder sql = new StringBuilder("""
                SELECT r.id, r.reference, st.label_fr, r.workflow_status, r.publication_status,
                       d.name_fr, su.display_name, r.created_at, ST_X(r.location), ST_Y(r.location)
                FROM report r
                JOIN service_type st ON st.id = r.service_type_id
                LEFT JOIN department d ON d.id = r.department_id
                LEFT JOIN staff_user su ON su.id = r.assignee_id
                WHERE 1=1
                """);
        Map<String, Object> params = new HashMap<>();
        applyScope(sql, params, user);
        if (filter.status() != null) {
            sql.append(" AND r.workflow_status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.publication() != null) {
            sql.append(" AND r.publication_status = :publication");
            params.put("publication", filter.publication().name());
        }
        if (filter.departmentId() != null) {
            sql.append(" AND r.department_id = :dept");
            params.put("dept", filter.departmentId());
        }
        if (filter.onlyMine()) {
            sql.append(" AND r.assignee_id = :me");
            params.put("me", user.getId());
        }
        if (filter.onlyUnassigned()) {
            sql.append(" AND r.assignee_id IS NULL");
        }
        sql.append(" ORDER BY r.created_at DESC, r.id LIMIT :limit OFFSET :offset");
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        query.setParameter("limit", Math.min(limit, 100));
        query.setParameter("offset", Math.max(offset, 0));
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        List<WorkQueueRow> out = new ArrayList<>();
        for (Object[] r : rows) {
            out.add(new WorkQueueRow((UUID) r[0], (String) r[1], (String) r[2],
                    WorkflowStatus.valueOf((String) r[3]), PublicationStatus.valueOf((String) r[4]),
                    (String) r[5], (String) r[6], toInstant(r[7]),
                    ((Number) r[8]).doubleValue(), ((Number) r[9]).doubleValue()));
        }
        return out;
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public long workQueueCount(String username, WorkQueueFilter filter) {
        StaffUser user = requireUser(username);
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM report r WHERE 1=1");
        Map<String, Object> params = new HashMap<>();
        applyScope(sql, params, user);
        if (filter.status() != null) {
            sql.append(" AND r.workflow_status = :status");
            params.put("status", filter.status().name());
        }
        if (filter.publication() != null) {
            sql.append(" AND r.publication_status = :publication");
            params.put("publication", filter.publication().name());
        }
        if (filter.departmentId() != null) {
            sql.append(" AND r.department_id = :dept");
            params.put("dept", filter.departmentId());
        }
        if (filter.onlyMine()) {
            sql.append(" AND r.assignee_id = :me");
            params.put("me", user.getId());
        }
        if (filter.onlyUnassigned()) {
            sql.append(" AND r.assignee_id IS NULL");
        }
        Query query = em.createNativeQuery(sql.toString());
        params.forEach(query::setParameter);
        return ((Number) query.getSingleResult()).longValue();
    }

    private void applyScope(StringBuilder sql, Map<String, Object> params, StaffUser user) {
        if (!seesAll(user)) {
            List<UUID> departmentIds = user.getDepartments().stream()
                    .map(tn.civiccare.identity.Department::getId).toList();
            if (departmentIds.isEmpty()) {
                sql.append(" AND 1=0");
            } else {
                sql.append(" AND r.department_id IN (:scopeDepts)");
                params.put("scopeDepts", departmentIds);
            }
        }
    }

    /** L'accès objet est revérifié pour chaque dossier ouvert (pas seulement en liste). */
    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public boolean canAccess(String username, UUID reportDepartmentId) {
        StaffUser user = requireUser(username);
        if (seesAll(user)) {
            return true;
        }
        return reportDepartmentId != null && user.getDepartments().stream()
                .anyMatch(d -> d.getId().equals(reportDepartmentId));
    }

    // ===== Tableau de bord =====

    public record DashboardStats(long total, long open, long inProgress, long pendingReview,
                                 Map<String, Long> byDepartment, Double avgCloseDays,
                                 long outboxBacklog, long oldestOpenDays) {
    }

    /**
     * Statistiques réelles. Définitions documentées :
     * - avgCloseDays : moyenne (closed_at - created_at) des dossiers clos ;
     * - oldestOpenDays : ancienneté du plus vieux dossier non clos ;
     * - backlog : messages outbox en attente d'envoi.
     */
    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public DashboardStats dashboard() {
        Object[] counts = (Object[]) em.createNativeQuery("""
                SELECT count(*),
                       count(*) FILTER (WHERE workflow_status = 'OPEN'),
                       count(*) FILTER (WHERE workflow_status = 'IN_PROGRESS'),
                       count(*) FILTER (WHERE publication_status = 'PENDING_REVIEW'),
                       avg(EXTRACT(EPOCH FROM (closed_at - created_at)) / 86400.0)
                           FILTER (WHERE closed_at IS NOT NULL),
                       coalesce(max(EXTRACT(EPOCH FROM (now() - created_at)) / 86400.0)
                           FILTER (WHERE closed_at IS NULL), 0)
                FROM report
                """).getSingleResult();
        @SuppressWarnings("unchecked")
        List<Object[]> deptRows = em.createNativeQuery("""
                SELECT d.name_fr, count(r.id)
                FROM department d LEFT JOIN report r ON r.department_id = d.id
                GROUP BY d.name_fr ORDER BY count(r.id) DESC
                """).getResultList();
        Map<String, Long> byDepartment = new LinkedHashMap<>();
        deptRows.forEach(r -> byDepartment.put((String) r[0], ((Number) r[1]).longValue()));
        Number backlog = (Number) em.createNativeQuery(
                "SELECT count(*) FROM outbox_message WHERE status = 'PENDING'").getSingleResult();
        return new DashboardStats(((Number) counts[0]).longValue(), ((Number) counts[1]).longValue(),
                ((Number) counts[2]).longValue(), ((Number) counts[3]).longValue(),
                byDepartment,
                counts[4] == null ? null : ((Number) counts[4]).doubleValue(),
                backlog.longValue(),
                Math.round(((Number) counts[5]).doubleValue()));
    }

    /**
     * Export CSV de la file de travail, protégé contre l'injection de formules :
     * toute cellule commençant par = + - @ est préfixée d'une apostrophe.
     */
    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public String exportCsv(String username, WorkQueueFilter filter) {
        StringBuilder csv = new StringBuilder("reference;type;statut;publication;equipe;agent;cree_le\n");
        int offset = 0;
        List<WorkQueueRow> page;
        do {
            page = workQueue(username, filter, offset, 100);
            for (WorkQueueRow row : page) {
                csv.append(String.join(";",
                        csvCell(row.reference()), csvCell(row.typeLabelFr()),
                        csvCell(row.status().name()), csvCell(row.publication().name()),
                        csvCell(row.departmentName()), csvCell(row.assigneeName()),
                        csvCell(row.createdAt().toString()))).append('\n');
            }
            offset += 100;
        } while (page.size() == 100);
        return csv.toString();
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        if (!v.isEmpty() && (v.charAt(0) == '=' || v.charAt(0) == '+' || v.charAt(0) == '-'
                || v.charAt(0) == '@')) {
            v = "'" + v;
        }
        return '"' + v + '"';
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        return ((java.sql.Timestamp) value).toInstant();
    }
}
