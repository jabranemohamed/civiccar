package tn.civiccare.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tn.civiccare.administration.AdminFacade;
import tn.civiccare.administration.AdminFacade.WorkQueueFilter;
import tn.civiccare.administration.AuditService;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.RoutingRule;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.content.ContactMessage;
import tn.civiccare.content.ContentPage;
import tn.civiccare.content.ContentService;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.Role;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserService;
import tn.civiccare.media.MediaAsset;
import tn.civiccare.media.MediaService;
import tn.civiccare.moderation.ModerationService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.*;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.i18n.TranslationProvider;

import java.util.*;

/**
 * API d'administration de la SPA. La chaîne de sécurité exige déjà un rôle interne ;
 * chaque opération repasse en plus par les contrôles service (@PreAuthorize + accès
 * objet par équipe) — l'UI n'est jamais l'autorité.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminApi {

    private final AdminFacade admin;
    private final ReportAdminQueries adminQueries;
    private final WorkflowService workflow;
    private final ModerationService moderation;
    private final MediaService media;
    private final CatalogService catalog;
    private final StaffUserService users;
    private final ContentService content;
    private final AuditService audit;
    private final BoundaryService boundary;
    private final AppProperties props;

    public AdminApi(AdminFacade admin, ReportAdminQueries adminQueries, WorkflowService workflow,
                    ModerationService moderation, MediaService media, CatalogService catalog,
                    StaffUserService users, ContentService content, AuditService audit,
                    BoundaryService boundary, AppProperties props) {
        this.admin = admin;
        this.adminQueries = adminQueries;
        this.workflow = workflow;
        this.moderation = moderation;
        this.media = media;
        this.catalog = catalog;
        this.users = users;
        this.content = content;
        this.audit = audit;
        this.boundary = boundary;
        this.props = props;
    }

    // ===== Tableau de bord =====

    @GetMapping("/dashboard")
    public AdminFacade.DashboardStats dashboard() {
        return admin.dashboard();
    }

    // ===== File de travail =====

    @GetMapping("/reports")
    public Map<String, Object> workQueue(Authentication auth,
                                         @RequestParam(required = false) WorkflowStatus status,
                                         @RequestParam(required = false) PublicationStatus publication,
                                         @RequestParam(required = false) UUID departmentId,
                                         @RequestParam(defaultValue = "false") boolean onlyMine,
                                         @RequestParam(defaultValue = "false") boolean onlyUnassigned,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        WorkQueueFilter filter = new WorkQueueFilter(status, publication, departmentId,
                onlyMine, onlyUnassigned);
        return Map.of(
                "total", admin.workQueueCount(auth.getName(), filter),
                "items", admin.workQueue(auth.getName(), filter,
                        Math.max(0, page) * Math.min(size, 100), Math.min(size, 100)));
    }

    @GetMapping(value = "/reports/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(Authentication auth,
                                            @RequestParam(required = false) WorkflowStatus status,
                                            @RequestParam(required = false) PublicationStatus publication,
                                            @RequestParam(required = false) UUID departmentId) {
        String csv = admin.exportCsv(auth.getName(),
                new WorkQueueFilter(status, publication, departmentId, false, false));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"dossiers.csv\"")
                .body(csv);
    }

    // ===== Dossier interne =====

    @GetMapping("/reports/{id}")
    public ResponseEntity<Map<String, Object>> reportDetail(Authentication auth, @PathVariable UUID id) {
        Report report = adminQueries.byId(id).orElse(null);
        if (report == null || !admin.canAccess(auth.getName(),
                report.getDepartment() == null ? null : report.getDepartment().getId())) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", report.getId());
        dto.put("reference", report.getReference());
        dto.put("version", report.getVersion());
        dto.put("type", Map.of("id", report.getServiceType().getId(),
                "code", report.getServiceType().getCode(),
                "labelFr", report.getServiceType().getLabelFr(),
                "labels", Map.of(
                        "fr", report.getServiceType().getLabelFr(),
                        "ar", report.getServiceType().getLabelAr(),
                        "en", report.getServiceType().label(TranslationProvider.ENGLISH))));
        dto.put("workflowStatus", report.getWorkflowStatus().name());
        dto.put("publicationStatus", report.getPublicationStatus().name());
        dto.put("allowedTransitions", report.getWorkflowStatus().allowedTransitions().stream()
                .map(Enum::name).sorted().toList());
        dto.put("department", report.getDepartment() == null ? null
                : Map.of("id", report.getDepartment().getId(),
                "nameFr", report.getDepartment().getNameFr(),
                "nameAr", report.getDepartment().getNameAr()));
        dto.put("assignee", report.getAssignee() == null ? null
                : Map.of("id", report.getAssignee().getId(),
                "displayName", report.getAssignee().getDisplayName()));
        dto.put("address", report.getAddress());
        dto.put("position", Map.of("lon", report.getLocation().getX(), "lat", report.getLocation().getY()));
        dto.put("descriptionPrivate", report.getDescriptionPrivate());
        dto.put("descriptionPublic", report.getDescriptionPublic());
        dto.put("fieldValues", report.getFieldValues());
        dto.put("createdAt", report.getCreatedAt());
        dto.put("closedAt", report.getClosedAt());
        dto.put("archivedAt", report.getArchivedAt());
        dto.put("personalDataPurged", report.getPersonalDataPurgedAt() != null);

        adminQueries.contact(id).ifPresent(contact -> dto.put("contact", Map.of(
                "purged", contact.isPurged(),
                "email", contact.getEmail() == null ? "" : contact.getEmail(),
                "phone", contact.getPhone() == null ? "" : contact.getPhone(),
                "vehiclePlate", contact.getVehiclePlate() == null ? "" : contact.getVehiclePlate())));

        dto.put("media", media.allFor(id).stream()
                .<Map<String, Object>>map(m -> Map.of(
                        "id", m.getId(),
                        "thumb", "/media/" + m.getThumbKey(),
                        "full", "/media/" + m.getStorageKey(),
                        "moderationStatus", m.getModerationStatus().name()))
                .toList());
        dto.put("statusHistory", adminQueries.statusHistory(id).stream()
                .<Map<String, Object>>map(e -> {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("at", e.getCreatedAt());
                    entry.put("from", e.getFromStatus() == null ? null : e.getFromStatus().name());
                    entry.put("to", e.getToStatus().name());
                    entry.put("reason", e.getReason());
                    return entry;
                }).toList());
        dto.put("publicUpdates", adminQueries.publicUpdates(id).stream()
                .<Map<String, Object>>map(u -> Map.of("at", u.getCreatedAt(), "body", u.getBody()))
                .toList());
        dto.put("internalNotes", adminQueries.internalNotes(id).stream()
                .<Map<String, Object>>map(n -> Map.of("at", n.getCreatedAt(), "body", n.getBody()))
                .toList());
        adminQueries.duplicateLink(id).flatMap(link -> adminQueries.plainById(link.getCanonicalId()))
                .ifPresent(canonical -> dto.put("duplicateOf", canonical.getReference()));
        return ResponseEntity.ok(dto);
    }

    /** Détection de version périmée côté API (M08) : 409 sans écraser l'autre agent. */
    private void checkVersion(UUID reportId, Long expectedVersion) {
        if (expectedVersion == null) {
            return;
        }
        Report current = adminQueries.plainById(reportId)
                .orElseThrow(() -> new ValidationException("report.unknown"));
        if (current.getVersion() != expectedVersion) {
            throw new ValidationException("version.stale");
        }
    }

    public record AssignRequest(UUID departmentId, UUID assigneeId, Long expectedVersion) {
    }

    @PostMapping("/reports/{id}/assign")
    public void assign(Authentication auth, @PathVariable UUID id, @RequestBody AssignRequest request) {
        checkVersion(id, request.expectedVersion());
        workflow.assign(auth.getName(), id, request.departmentId(), request.assigneeId());
    }

    public record StatusRequest(WorkflowStatus target, String reason, Long expectedVersion) {
    }

    @PostMapping("/reports/{id}/status")
    public void changeStatus(Authentication auth, @PathVariable UUID id, @RequestBody StatusRequest request) {
        checkVersion(id, request.expectedVersion());
        workflow.changeStatus(auth.getName(), id, request.target(), request.reason());
    }

    public record BodyRequest(String body) {
    }

    @PostMapping("/reports/{id}/public-update")
    public void addPublicUpdate(Authentication auth, @PathVariable UUID id, @RequestBody BodyRequest request) {
        if (request.body() == null || request.body().isBlank()) {
            throw new ValidationException("contact.fields");
        }
        workflow.addPublicUpdate(auth.getName(), id, request.body());
    }

    @PostMapping("/reports/{id}/note")
    public void addNote(Authentication auth, @PathVariable UUID id, @RequestBody BodyRequest request) {
        if (request.body() == null || request.body().isBlank()) {
            throw new ValidationException("contact.fields");
        }
        workflow.addInternalNote(auth.getName(), id, request.body());
    }

    public record PublicationRequest(PublicationStatus status, String reason) {
    }

    @PostMapping("/reports/{id}/publication")
    public void setPublication(Authentication auth, @PathVariable UUID id,
                               @RequestBody PublicationRequest request) {
        moderation.setPublication(auth.getName(), id, request.status(), request.reason());
    }

    public record PublicTextRequest(String text) {
    }

    @PostMapping("/reports/{id}/public-text")
    public void editPublicText(Authentication auth, @PathVariable UUID id,
                               @RequestBody PublicTextRequest request) {
        moderation.editPublicText(auth.getName(), id, request.text());
    }

    public record CategoryRequest(UUID typeId) {
    }

    @PostMapping("/reports/{id}/category")
    public void changeCategory(Authentication auth, @PathVariable UUID id,
                               @RequestBody CategoryRequest request) {
        moderation.changeCategory(auth.getName(), id, request.typeId());
    }

    public record DuplicateRequest(String canonicalReference) {
    }

    @PostMapping("/reports/{id}/duplicate")
    public void markDuplicate(Authentication auth, @PathVariable UUID id,
                              @RequestBody DuplicateRequest request) {
        moderation.markDuplicate(auth.getName(), id, request.canonicalReference());
    }

    public record MediaModerationRequest(boolean approve, String reason) {
    }

    @PostMapping("/reports/{id}/media/{mediaId}")
    public void moderateMedia(Authentication auth, @PathVariable UUID id, @PathVariable UUID mediaId,
                              @RequestBody MediaModerationRequest request) {
        moderation.moderateMedia(auth.getName(), id, mediaId, request.approve(), request.reason());
    }

    // ===== Référentiels =====

    @GetMapping("/departments")
    public List<Map<String, Object>> departments() {
        return users.allDepartments().stream()
                .<Map<String, Object>>map(d -> Map.of("id", d.getId(), "code", d.getCode(),
                        "nameFr", d.getNameFr(), "nameAr", d.getNameAr(),
                        "demo", d.isDemo(), "active", d.isActive()))
                .toList();
    }

    @GetMapping("/departments/{id}/agents")
    public List<Map<String, Object>> agentsOf(@PathVariable UUID id) {
        return users.agentsOfDepartment(id).stream()
                .<Map<String, Object>>map(u -> Map.of("id", u.getId(), "displayName", u.getDisplayName()))
                .toList();
    }

    // ===== Catalogue (ADMIN — contrôles service) =====

    @GetMapping("/catalog")
    public Map<String, Object> adminCatalog() {
        List<Map<String, Object>> types = catalog.allTypes().stream()
                .<Map<String, Object>>map(t -> Map.of(
                        "id", t.getId(), "code", t.getCode(),
                        "groupLabelFr", t.getGroup().getLabelFr(),
                        "groupLabels", Map.of(
                                "fr", t.getGroup().getLabelFr(),
                                "ar", t.getGroup().getLabelAr(),
                                "en", t.getGroup().label(TranslationProvider.ENGLISH)),
                        "labelFr", t.getLabelFr(), "labelAr", t.getLabelAr(),
                        "labelEn", t.label(TranslationProvider.ENGLISH),
                        "active", t.isActive()))
                .toList();
        List<Map<String, Object>> rules = catalog.allRoutingRules().stream()
                .<Map<String, Object>>map(r -> Map.of(
                        "id", r.getId(), "serviceTypeId", r.getServiceType().getId(),
                        "departmentId", r.getDepartment().getId(), "active", r.isActive()))
                .toList();
        return Map.of("types", types, "routingRules", rules);
    }

    public record TypeActiveRequest(boolean active) {
    }

    @PatchMapping("/catalog/types/{typeId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void setTypeActive(@PathVariable UUID typeId, @RequestBody TypeActiveRequest request) {
        catalog.setTypeActive(typeId, request.active());
    }

    public record RoutingRequest(UUID departmentId, boolean active) {
    }

    @PatchMapping("/catalog/rules/{ruleId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public void updateRouting(@PathVariable UUID ruleId, @RequestBody RoutingRequest request) {
        Department department = users.allDepartments().stream()
                .filter(d -> d.getId().equals(request.departmentId())).findFirst()
                .orElseThrow(() -> new ValidationException("department.unknown"));
        catalog.updateRouting(ruleId, department, request.active());
    }

    // ===== Utilisateurs (ADMIN — contrôles service) =====

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return users.allUsers().stream()
                .<Map<String, Object>>map(u -> Map.of(
                        "id", u.getId(), "username", u.getUsername(),
                        "displayName", u.getDisplayName(), "enabled", u.isEnabled(),
                        "roles", u.getRoles().stream().map(Enum::name).sorted().toList(),
                        "departments", u.getDepartments().stream()
                                .map(Department::getNameFr).sorted().toList()))
                .toList();
    }

    public record CreateUserRequest(String username, String password, String displayName,
                                    Set<Role> roles, Set<UUID> departmentIds) {
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody CreateUserRequest request) {
        StaffUser created = users.create(request.username(), request.password(), request.displayName(),
                request.roles() == null ? Set.of() : request.roles(),
                request.departmentIds() == null ? Set.of() : request.departmentIds());
        return Map.of("id", created.getId(), "username", created.getUsername());
    }

    public record UserEnabledRequest(boolean enabled) {
    }

    @PatchMapping("/users/{id}")
    public void setUserEnabled(@PathVariable UUID id, @RequestBody UserEnabledRequest request) {
        users.setEnabled(id, request.enabled());
    }

    // ===== Contenus (ADMIN) =====

    @GetMapping("/content")
    public List<Map<String, Object>> allContent() {
        return content.allPages().stream()
                .<Map<String, Object>>map(p -> Map.of(
                        "slug", p.getSlug(),
                        "titleFr", p.getTitleFr(), "titleAr", p.getTitleAr(),
                        "titleEn", p.getTitleEn() == null ? "" : p.getTitleEn(),
                        "bodyFr", p.getBodyFr(), "bodyAr", p.getBodyAr(),
                        "bodyEn", p.getBodyEn() == null ? "" : p.getBodyEn()))
                .toList();
    }

    public record ContentUpdateRequest(String titleFr, String titleAr, String titleEn,
                                       String bodyFr, String bodyAr, String bodyEn) {
    }

    @PutMapping("/content/{slug}")
    public void updateContent(@PathVariable String slug, @RequestBody ContentUpdateRequest request) {
        content.updatePage(slug, request.titleFr(), request.titleAr(), request.titleEn(),
                request.bodyFr(), request.bodyAr(), request.bodyEn(), null);
    }

    // ===== Boîte de contact =====

    @GetMapping("/contact-messages")
    public List<Map<String, Object>> inbox() {
        return content.inbox().stream()
                .<Map<String, Object>>map(m -> Map.of(
                        "id", m.getId(), "name", m.getName(), "email", m.getEmail(),
                        "body", m.getBody(), "status", m.getStatus().name(),
                        "createdAt", m.getCreatedAt(), "copyRequested", m.isCopyRequested()))
                .toList();
    }

    @PostMapping("/contact-messages/{id}/processed")
    public void markProcessed(@PathVariable UUID id) {
        content.markProcessed(id, null);
    }

    // ===== Paramètres et audit (ADMIN) =====

    @GetMapping("/settings")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> settings() {
        BoundaryService.BoundaryInfo info = boundary.activeBoundaryInfo();
        return Map.of(
                "boundary", Map.of("code", info.code(), "nameFr", info.nameFr(),
                        "source", info.source(), "license", info.license(), "demo", info.demo()),
                "timezone", props.timezone(),
                "countryCode", props.countryCode(),
                "duplicateRadiusMeters", props.duplicateRadiusMeters(),
                "archiveAfterDays", props.archiveAfterDays(),
                "purgeAfterDays", props.purgeAfterDays(),
                "geocoderMode", props.geo().geocoderMode(),
                "tileUrl", props.map().tileUrl(),
                "open311Jurisdiction", props.open311().jurisdictionId());
    }

    @GetMapping("/audit")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public List<Map<String, Object>> auditLog() {
        return audit.latest(100).stream()
                .<Map<String, Object>>map(e -> Map.of(
                        "at", e.getCreatedAt(), "actor", e.getActor(), "action", e.getAction(),
                        "targetType", e.getTargetType(), "targetId", e.getTargetId()))
                .toList();
    }
}
