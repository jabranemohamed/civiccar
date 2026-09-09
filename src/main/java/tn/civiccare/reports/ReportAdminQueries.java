package tn.civiccare.reports;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Lectures internes d'un dossier pour le back-office (contrôle d'accès objet en amont). */
@Service
public class ReportAdminQueries {

    private final ReportRepository reports;
    private final ReportContactRepository contacts;
    private final StatusEventRepository statusEvents;
    private final PublicUpdateRepository publicUpdates;
    private final InternalNoteRepository internalNotes;
    private final DuplicateLinkRepository duplicateLinks;

    public ReportAdminQueries(ReportRepository reports, ReportContactRepository contacts,
                              StatusEventRepository statusEvents, PublicUpdateRepository publicUpdates,
                              InternalNoteRepository internalNotes, DuplicateLinkRepository duplicateLinks) {
        this.reports = reports;
        this.contacts = contacts;
        this.statusEvents = statusEvents;
        this.publicUpdates = publicUpdates;
        this.internalNotes = internalNotes;
        this.duplicateLinks = duplicateLinks;
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public Optional<Report> byId(UUID id) {
        return reports.findById(id).map(r -> {
            r.getServiceType().getGroup().getCode();
            r.getServiceType().getFields().forEach(f -> f.getOptions().size());
            if (r.getDepartment() != null) {
                r.getDepartment().getCode();
            }
            if (r.getAssignee() != null) {
                r.getAssignee().getUsername();
            }
            return r;
        });
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public Optional<ReportContact> contact(UUID reportId) {
        return contacts.findById(reportId);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<StatusEvent> statusHistory(UUID reportId) {
        return statusEvents.findByReportIdOrderByCreatedAt(reportId);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<PublicUpdate> publicUpdates(UUID reportId) {
        return publicUpdates.findByReportIdOrderByCreatedAt(reportId);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<InternalNote> internalNotes(UUID reportId) {
        return internalNotes.findByReportIdOrderByCreatedAt(reportId);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public Optional<DuplicateLink> duplicateLink(UUID reportId) {
        return duplicateLinks.findByReportId(reportId);
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public Optional<Report> plainById(UUID id) {
        return reports.findById(id);
    }
}
