package tn.civiccare.moderation;

import io.opentelemetry.api.common.Attributes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.administration.AuditService;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserRepository;
import tn.civiccare.media.MediaAsset;
import tn.civiccare.media.MediaService;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.DuplicateLink;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Modération : publication manuelle avant diffusion (politique initiale), masquage,
 * adaptation du texte public, gestion des photos, correction de catégorie, doublons.
 * La modération n'arrête jamais le traitement du dossier.
 */
@Service
public class ModerationService {

    public interface ModerationDecisionRepository extends JpaRepository<ModerationDecision, UUID> {
        List<ModerationDecision> findByReportIdOrderByCreatedAt(UUID reportId);
    }

    public interface DuplicateLinkJpa extends JpaRepository<DuplicateLink, UUID> {
        Optional<DuplicateLink> findByReportId(UUID reportId);
    }

    private final ReportRepository reports;
    private final ModerationDecisionRepository decisions;
    private final DuplicateLinkJpa duplicates;
    private final StaffUserRepository staffUsers;
    private final MediaService media;
    private final CatalogService catalog;
    private final AuditService audit;
    private final Telemetry telemetry;
    private final Clock clock;

    public ModerationService(ReportRepository reports, ModerationDecisionRepository decisions,
                             DuplicateLinkJpa duplicates, StaffUserRepository staffUsers,
                             MediaService media, CatalogService catalog, AuditService audit,
                             Telemetry telemetry, Clock clock) {
        this.reports = reports;
        this.decisions = decisions;
        this.duplicates = duplicates;
        this.staffUsers = staffUsers;
        this.media = media;
        this.catalog = catalog;
        this.audit = audit;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    private StaffUser actor(String username) {
        return staffUsers.findByUsername(username)
                .orElseThrow(() -> new ValidationException("user.unknown"));
    }

    private Report report(UUID reportId) {
        return reports.findById(reportId).orElseThrow(() -> new ValidationException("report.unknown"));
    }

    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Transactional
    public void setPublication(String username, UUID reportId, PublicationStatus status, String reason) {
        telemetry.runSpan("report.moderate", Attributes.empty(), () -> {
            Report report = report(reportId);
            StaffUser user = actor(username);
            report.setPublicationStatus(status);
            report.touch(clock.instant());
            ModerationDecision.Decision decision = status == PublicationStatus.PUBLISHED
                    ? ModerationDecision.Decision.PUBLISH : ModerationDecision.Decision.HIDE;
            decisions.save(new ModerationDecision(UUID.randomUUID(), reportId, decision,
                    user.getId(), reason, clock.instant()));
            audit.record(username, "MODERATION_" + status.name(), "report", report.getReference(),
                    reason == null ? null : Map.of("reason", reason));
        });
    }

    /** Adapte le texte public (retrait de données personnelles, clarification) sans toucher au texte source. */
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Transactional
    public void editPublicText(String username, UUID reportId, String newPublicText) {
        Report report = report(reportId);
        StaffUser user = actor(username);
        report.setDescriptionPublic(newPublicText == null ? null : newPublicText.trim());
        report.touch(clock.instant());
        decisions.save(new ModerationDecision(UUID.randomUUID(), reportId,
                ModerationDecision.Decision.EDIT_PUBLIC_TEXT, user.getId(), null, clock.instant()));
        audit.record(username, "EDIT_PUBLIC_TEXT", "report", report.getReference(), null);
    }

    /** Approuve ou retire une photo sans arrêter le traitement ni perdre le dossier. */
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Transactional
    public void moderateMedia(String username, UUID reportId, UUID assetId, boolean approve, String reason) {
        Report report = report(reportId);
        StaffUser user = actor(username);
        media.setModeration(assetId, approve ? MediaAsset.ModerationStatus.APPROVED
                : MediaAsset.ModerationStatus.REMOVED);
        decisions.save(new ModerationDecision(UUID.randomUUID(), reportId,
                approve ? ModerationDecision.Decision.APPROVE_MEDIA : ModerationDecision.Decision.REMOVE_MEDIA,
                user.getId(), reason, clock.instant()));
        audit.record(username, approve ? "APPROVE_MEDIA" : "REMOVE_MEDIA", "report",
                report.getReference(), null);
    }

    /** Correction de catégorie auditée, avec nouvelle affectation selon les règles de routage. */
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Transactional
    public void changeCategory(String username, UUID reportId, UUID newTypeId) {
        Report report = report(reportId);
        StaffUser user = actor(username);
        ServiceType newType = catalog.typeById(newTypeId)
                .orElseThrow(() -> new ValidationException("type.invalid"));
        String oldCode = report.getServiceType().getCode();
        report.setServiceType(newType);
        catalog.routeFor(newType.getId()).ifPresent(report::setDepartment);
        report.touch(clock.instant());
        decisions.save(new ModerationDecision(UUID.randomUUID(), reportId,
                ModerationDecision.Decision.CHANGE_CATEGORY, user.getId(),
                oldCode + " -> " + newType.getCode(), clock.instant()));
        audit.record(username, "CHANGE_CATEGORY", "report", report.getReference(),
                Map.of("from", oldCode, "to", newType.getCode()));
    }

    /**
     * Doublon confirmé : lien vers le dossier canonique (contrainte SQL anti-boucle),
     * sans exposer la cible si elle n'est pas publiée.
     */
    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void markDuplicate(String username, UUID reportId, String canonicalReference) {
        Report report = report(reportId);
        StaffUser user = actor(username);
        Report canonical = reports.findByReference(canonicalReference)
                .orElseThrow(() -> new ValidationException("canonical.unknown"));
        if (canonical.getId().equals(reportId)) {
            throw new ValidationException("duplicate.self");
        }
        // Anti-boucle : le canonique ne doit pas être lui-même marqué doublon.
        if (duplicates.findByReportId(canonical.getId()).isPresent()) {
            throw new ValidationException("duplicate.chain");
        }
        duplicates.save(new DuplicateLink(UUID.randomUUID(), reportId, canonical.getId(),
                user.getId(), clock.instant()));
        audit.record(username, "MARK_DUPLICATE", "report", report.getReference(),
                Map.of("canonical", canonical.getReference()));
    }

    @Transactional(readOnly = true)
    public List<ModerationDecision> decisionsFor(UUID reportId) {
        return decisions.findByReportIdOrderByCreatedAt(reportId);
    }
}
