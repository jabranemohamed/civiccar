package tn.civiccare.reports;

import io.opentelemetry.api.common.Attributes;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.administration.AuditService;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.DepartmentRepository;
import tn.civiccare.identity.Role;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserRepository;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.notifications.EmailOutboxService;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.i18n.TranslationProvider;
import tn.civiccare.subscriptions.SubscriptionService;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Transitions de workflow, affectations et réouvertures. Contrôles d'accès appliqués
 * au niveau service (pas seulement l'UI) : un AGENT n'agit que sur les dossiers de
 * ses équipes.
 */
@Service
public class WorkflowService {

    private final ReportRepository reports;
    private final StatusEventRepository statusEvents;
    private final PublicUpdateRepository publicUpdates;
    private final InternalNoteRepository internalNotes;
    private final StaffUserRepository staffUsers;
    private final DepartmentRepository departments;
    private final SubscriptionService subscriptions;
    private final EmailOutboxService emailOutbox;
    private final EmailComposer emails;
    private final AuditService audit;
    private final Telemetry telemetry;
    private final Clock clock;
    private final TranslationProvider translations;

    public WorkflowService(ReportRepository reports, StatusEventRepository statusEvents,
                           PublicUpdateRepository publicUpdates, InternalNoteRepository internalNotes,
                           StaffUserRepository staffUsers, DepartmentRepository departments,
                           SubscriptionService subscriptions,
                           EmailOutboxService emailOutbox, EmailComposer emails,
                           AuditService audit, Telemetry telemetry, Clock clock,
                           TranslationProvider translations) {
        this.reports = reports;
        this.statusEvents = statusEvents;
        this.publicUpdates = publicUpdates;
        this.internalNotes = internalNotes;
        this.staffUsers = staffUsers;
        this.departments = departments;
        this.subscriptions = subscriptions;
        this.emailOutbox = emailOutbox;
        this.emails = emails;
        this.audit = audit;
        this.telemetry = telemetry;
        this.clock = clock;
        this.translations = translations;
    }

    /** L'agent doit appartenir à l'équipe du dossier (ou être ADMIN/MODERATOR). */
    private StaffUser requireAccess(String username, Report report) {
        StaffUser user = staffUsers.findByUsername(username)
                .orElseThrow(() -> new ValidationException("user.unknown"));
        boolean privileged = user.getRoles().contains(Role.ADMIN) || user.getRoles().contains(Role.MODERATOR);
        if (!privileged) {
            Department dept = report.getDepartment();
            boolean member = dept != null && user.getDepartments().stream()
                    .anyMatch(d -> d.getId().equals(dept.getId()));
            if (!member) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Dossier hors du périmètre de vos équipes");
            }
        }
        return user;
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void changeStatus(String username, UUID reportId, WorkflowStatus target, String reason) {
        telemetry.runSpan("report.change_status", Attributes.empty(), () -> {
            Report report = reports.findById(reportId)
                    .orElseThrow(() -> new ValidationException("report.unknown"));
            StaffUser actor = requireAccess(username, report);
            WorkflowStatus from = report.getWorkflowStatus();
            if (from == target) {
                return;
            }
            if (!from.allowedTransitions().contains(target)) {
                throw new ValidationException("transition.invalid");
            }
            boolean reopening = from.isTerminal();
            if (reopening && (reason == null || reason.isBlank())) {
                throw new ValidationException("reopen.reasonRequired");
            }
            Instant now = clock.instant();
            report.setWorkflowStatus(target);
            report.touch(now);
            if (target.isTerminal()) {
                report.setClosedAt(now);
            } else {
                // Réouverture : efface les échéances courantes sans effacer l'historique.
                report.setClosedAt(null);
                report.setArchivedAt(null);
            }
            statusEvents.save(new StatusEvent(UUID.randomUUID(), reportId, from, target,
                    actor.getId(), reason, now));
            audit.record(username, "STATUS_" + target.name(), "report", report.getReference(),
                    reason == null ? null : Map.of("reason", reason));
            telemetry.transition(from.name(), target.name());
            notifySubscribers(report, target);
        });
    }

    /** Notification des abonnés actifs, dédupliquée par (dossier, événement, abonné). */
    private void notifySubscribers(Report report, WorkflowStatus target) {
        long eventStamp = clock.instant().toEpochMilli();
        for (SubscriptionService.ActiveSubscriber sub : subscriptions.activeSubscribers(report.getId())) {
            Locale locale = TranslationProvider.FRENCH;
            String statusLabel = translations.getTranslation("status." + target.name(), locale);
            emailOutbox.enqueueEmail("report.status", sub.email(),
                    emails.subject(locale, "email.report.status.subject", report.getReference()),
                    emails.statusChangedBody(locale, report.getReference(), statusLabel, sub.unsubscribeUrl()),
                    "status:%s:%s:%s:%d".formatted(report.getId(), target, sub.subscriptionId(), eventStamp));
        }
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void assign(String username, UUID reportId, UUID departmentId, UUID assigneeId) {
        telemetry.runSpan("report.assign", Attributes.empty(), () -> {
            Report report = reports.findById(reportId)
                    .orElseThrow(() -> new ValidationException("report.unknown"));
            StaffUser actor = requireAccess(username, report);
            if (departmentId != null) {
                Department target = actor.getRoles().contains(Role.ADMIN)
                        || actor.getRoles().contains(Role.MODERATOR)
                        ? findDepartment(departmentId)
                        : actor.getDepartments().stream()
                        .filter(d -> d.getId().equals(departmentId)).findFirst()
                        .orElse(findDepartment(departmentId)); // réaffectation sortante autorisée
                report.setDepartment(target);
            }
            if (assigneeId != null) {
                StaffUser assignee = staffUsers.findById(assigneeId)
                        .orElseThrow(() -> new ValidationException("assignee.unknown"));
                // L'agent affecté doit être autorisé sur l'équipe du dossier.
                if (report.getDepartment() != null && assignee.getDepartments().stream()
                        .noneMatch(d -> d.getId().equals(report.getDepartment().getId()))
                        && !assignee.getRoles().contains(Role.ADMIN)) {
                    throw new ValidationException("assignee.notInDepartment");
                }
                report.setAssignee(assignee);
            } else {
                report.setAssignee(null);
            }
            report.touch(clock.instant());
            audit.record(username, "ASSIGN", "report", report.getReference(), null);
        });
    }

    private Department findDepartment(UUID id) {
        return departments.findById(id)
                .orElseThrow(() -> new ValidationException("department.unknown"));
    }

    /** Message public visible sur la fiche. */
    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void addPublicUpdate(String username, UUID reportId, String body) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ValidationException("report.unknown"));
        StaffUser actor = requireAccess(username, report);
        publicUpdates.save(new PublicUpdate(UUID.randomUUID(), reportId, actor.getId(),
                body.trim(), clock.instant()));
        audit.record(username, "PUBLIC_UPDATE", "report", report.getReference(), null);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void addInternalNote(String username, UUID reportId, String body) {
        Report report = reports.findById(reportId)
                .orElseThrow(() -> new ValidationException("report.unknown"));
        StaffUser actor = requireAccess(username, report);
        internalNotes.save(new InternalNote(UUID.randomUUID(), reportId, actor.getId(),
                body.trim(), clock.instant()));
    }
}
