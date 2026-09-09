package tn.civiccare.reports;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.catalog.FieldDefinition;
import tn.civiccare.media.MediaAsset;
import tn.civiccare.media.MediaService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Assemblage de la fiche publique. Liste blanche stricte : jamais de contact, de note
 * interne, de texte source privé, de plaque ni de média non approuvé — quel que soit
 * le canal (UI, API, carte, erreurs).
 */
@Service
public class PublicReportFacade {

    private final ReportRepository reports;
    private final StatusEventRepository statusEvents;
    private final PublicUpdateRepository publicUpdates;
    private final MediaService media;

    public PublicReportFacade(ReportRepository reports, StatusEventRepository statusEvents,
                              PublicUpdateRepository publicUpdates, MediaService media) {
        this.reports = reports;
        this.statusEvents = statusEvents;
        this.publicUpdates = publicUpdates;
        this.media = media;
    }

    public record TimelineEntry(Instant at, WorkflowStatus toStatus, String publicMessage) {
    }

    public record PublicDetail(UUID id, String reference, String typeLabel, String groupLabel,
                               WorkflowStatus status, boolean archived,
                               String address, String addressDetails, String description,
                               double longitude, double latitude, Instant createdAt,
                               Map<String, String> publicFieldLabels,
                               List<String> mediaKeys, List<String> thumbKeys,
                               List<TimelineEntry> timeline) {
    }

    /** Fiche publique par référence : Optional.empty() pour inexistant OU non publié (aucune distinction). */
    @Transactional(readOnly = true)
    public Optional<PublicDetail> byReference(String reference, Locale locale) {
        Optional<Report> found = reports.findByReference(reference)
                .filter(Report::isPubliclyVisible);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Report report = found.get();
        var type = report.getServiceType();

        // Champs conditionnels : uniquement ceux marqués publics, avec libellés traduits.
        Map<String, String> publicFields = new LinkedHashMap<>();
        for (FieldDefinition field : type.getFields()) {
            if (!field.isPublicField()) {
                continue;
            }
            String value = report.getFieldValues().get(field.getCode());
            if (value == null) {
                continue;
            }
            String display = field.getKind() == FieldDefinition.Kind.SELECT
                    ? field.getOptions().stream()
                    .filter(o -> o.getCode().equals(value)).findFirst()
                    .map(o -> o.label(locale)).orElse(value)
                    : value;
            publicFields.put(field.label(locale), display);
        }

        List<MediaAsset> approved = media.approvedFor(report.getId());

        List<TimelineEntry> timeline = new ArrayList<>();
        List<StatusEvent> events = statusEvents.findByReportIdOrderByCreatedAt(report.getId());
        List<PublicUpdate> updates = publicUpdates.findByReportIdOrderByCreatedAt(report.getId());
        for (StatusEvent event : events) {
            timeline.add(new TimelineEntry(event.getCreatedAt(), event.getToStatus(), null));
        }
        for (PublicUpdate update : updates) {
            timeline.add(new TimelineEntry(update.getCreatedAt(), null, update.getBody()));
        }
        timeline.sort(java.util.Comparator.comparing(TimelineEntry::at));

        return Optional.of(new PublicDetail(report.getId(), report.getReference(),
                type.label(locale), type.getGroup().label(locale),
                report.getWorkflowStatus(), report.getArchivedAt() != null,
                report.getAddress(), report.getAddressDetails(), report.getDescriptionPublic(),
                report.getLocation().getX(), report.getLocation().getY(), report.getCreatedAt(),
                publicFields,
                approved.stream().map(MediaAsset::getStorageKey).toList(),
                approved.stream().map(MediaAsset::getThumbKey).toList(),
                timeline));
    }
}
