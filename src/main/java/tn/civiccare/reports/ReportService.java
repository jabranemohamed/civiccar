package tn.civiccare.reports;

import io.opentelemetry.api.common.Attributes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.FieldDefinition;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.geo.GeoPoints;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.DepartmentRepository;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.notifications.EmailOutboxService;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.subscriptions.SubscriptionService;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ReportService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportService.class);

    public static final int DESCRIPTION_MAX = 300;
    public static final String CONSENT_VERSION = "v1-2026";
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final ReportRepository reports;
    private final ReportContactRepository contacts;
    private final StatusEventRepository statusEvents;
    private final CatalogService catalog;
    private final BoundaryService boundary;
    private final DepartmentRepository departments;
    private final ReportReferenceGenerator referenceGenerator;
    private final PhoneNormalizer phoneNormalizer;
    private final EmailOutboxService emailOutbox;
    private final EmailComposer emails;
    private final SubscriptionService subscriptions;
    private final tn.civiccare.media.MediaService mediaService;
    private final Telemetry telemetry;
    private final Clock clock;
    private final AppProperties props;

    public ReportService(ReportRepository reports, ReportContactRepository contacts,
                         StatusEventRepository statusEvents, CatalogService catalog,
                         BoundaryService boundary, DepartmentRepository departments,
                         ReportReferenceGenerator referenceGenerator, PhoneNormalizer phoneNormalizer,
                         EmailOutboxService emailOutbox, EmailComposer emails,
                         SubscriptionService subscriptions, tn.civiccare.media.MediaService mediaService,
                         Telemetry telemetry, Clock clock, AppProperties props) {
        this.reports = reports;
        this.contacts = contacts;
        this.statusEvents = statusEvents;
        this.catalog = catalog;
        this.boundary = boundary;
        this.departments = departments;
        this.referenceGenerator = referenceGenerator;
        this.phoneNormalizer = phoneNormalizer;
        this.emailOutbox = emailOutbox;
        this.emails = emails;
        this.subscriptions = subscriptions;
        this.mediaService = mediaService;
        this.telemetry = telemetry;
        this.clock = clock;
        this.props = props;
    }

    public record CreateReportCommand(UUID serviceTypeId, double longitude, double latitude,
                                      String address, String addressDetails, String description,
                                      Map<String, String> fieldValues, String email, String phone,
                                      boolean consent, String idempotencyKey, Locale locale,
                                      java.util.List<tn.civiccare.media.ImageProcessor.Processed> photos) {
    }

    /**
     * Création d'un signalement sans compte. Toutes les validations sont serveur :
     * périmètre municipal, description, champs conditionnels, e-mail, téléphone, consentement.
     * Idempotence : un double envoi avec la même clé retourne le dossier existant.
     * L'accusé de réception est enregistré dans l'outbox dans la même transaction.
     */
    @Transactional
    public Report create(CreateReportCommand cmd) {
        return telemetry.span("report.create", Attributes.empty(), () -> doCreate(cmd));
    }

    private Report doCreate(CreateReportCommand cmd) {
        if (cmd.idempotencyKey() != null) {
            Optional<Report> existing = reports.findByIdempotencyKey(cmd.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        ServiceType type = catalog.typeById(cmd.serviceTypeId())
                .filter(ServiceType::isActive)
                .orElseThrow(() -> new ValidationException("type.invalid"));

        if (!boundary.isInsideBoundary(cmd.longitude(), cmd.latitude())) {
            throw new ValidationException("position.outside");
        }

        String description = normalizeDescription(cmd.description());
        if (type.isStandardDescription()) {
            if (description == null || description.isEmpty() || description.length() > DESCRIPTION_MAX) {
                throw new ValidationException("description.invalid");
            }
        } else if (description != null && description.length() > DESCRIPTION_MAX) {
            throw new ValidationException("description.invalid");
        }

        if (cmd.email() == null || !EMAIL.matcher(cmd.email().trim()).matches()) {
            throw new ValidationException("email.invalid");
        }
        if (!cmd.consent()) {
            throw new ValidationException("consent.required");
        }
        String phone = phoneNormalizer.normalize(cmd.phone());

        Map<String, String> fieldValues = validateFields(type, cmd.fieldValues());
        // La plaque est stockée dans le contact privé, jamais dans les valeurs du dossier.
        String plate = fieldValues.remove("VEHICLE_PLATE");

        Instant now = clock.instant();
        Report report = new Report(UUID.randomUUID(), referenceGenerator.next(), type,
                GeoPoints.of(cmd.longitude(), cmd.latitude()), now);
        report.setAddress(trimTo(cmd.address(), 300));
        report.setAddressDetails(trimTo(cmd.addressDetails(), 300));
        report.setDescriptionPrivate(description);
        report.setDescriptionPublic(description);
        report.setFieldValues(fieldValues);
        report.setIdempotencyKey(cmd.idempotencyKey());
        report.setDepartment(catalog.routeFor(type.getId())
                .orElseGet(() -> departments.findByCode("TRIAGE").orElse(null)));
        reports.save(report);

        // Photos déjà validées/réencodées : persistance des dérivés dans la même transaction.
        if (cmd.photos() != null) {
            if (cmd.photos().size() > props.maxPhotosPerReport()) {
                throw new ValidationException("media.tooMany");
            }
            int sort = 0;
            for (var photo : cmd.photos()) {
                mediaService.attach(report.getId(), photo, sort++);
            }
        }

        contacts.save(new ReportContact(report.getId(), cmd.email().trim(), phone, trimTo(plate, 32),
                CONSENT_VERSION, now));
        statusEvents.save(new StatusEvent(UUID.randomUUID(), report.getId(), null,
                WorkflowStatus.OPEN, null, null, now));

        Locale locale = cmd.locale() == null ? Locale.forLanguageTag("fr") : cmd.locale();
        String subscribeUrl = subscriptions.prepareSubscription(report, cmd.email().trim(), now);
        emailOutbox.enqueueEmail("report.created", cmd.email().trim(),
                emails.subject(locale, "email.report.created.subject", report.getReference()),
                emails.reportCreatedBody(locale, report.getReference(), subscribeUrl),
                "report.created:" + report.getId());

        telemetry.reportCreated(type.getGroup().getCode());
        // Log non sensible (famille uniquement), corrélé à la trace report.create par l'agent
        log.info("Signalement créé (famille {})", type.getGroup().getCode());
        return report;
    }

    /** Normalisation documentée : trim + Unicode NFC. Le texte n'est pas autrement altéré. */
    static String normalizeDescription(String raw) {
        if (raw == null) {
            return null;
        }
        return Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
    }

    private Map<String, String> validateFields(ServiceType type, Map<String, String> values) {
        Map<String, String> input = values == null ? Map.of() : values;
        Map<String, String> validated = new HashMap<>();
        for (FieldDefinition field : type.getFields()) {
            String value = input.get(field.getCode());
            value = value == null ? null : value.trim();
            if (value == null || value.isEmpty()) {
                if (field.isRequired()) {
                    throw new ValidationException("field.required:" + field.getCode());
                }
                continue;
            }
            if (value.length() > field.getMaxLen()) {
                throw new ValidationException("field.tooLong:" + field.getCode());
            }
            if (field.getKind() == FieldDefinition.Kind.SELECT) {
                String v = value;
                boolean valid = field.getOptions().stream()
                        .anyMatch(o -> o.isActive() && o.getCode().equals(v));
                if (!valid) {
                    throw new ValidationException("field.invalidOption:" + field.getCode());
                }
            }
            validated.put(field.getCode(), value);
        }
        // Clés inconnues rejetées : le schéma des champs est borné.
        for (String key : input.keySet()) {
            if (!key.isBlank() && type.getFields().stream().noneMatch(f -> f.getCode().equals(key))
                    && input.get(key) != null && !input.get(key).isBlank()) {
                throw new ValidationException("field.unknown:" + key);
            }
        }
        return validated;
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.substring(0, Math.min(t.length(), max));
    }

    @Transactional(readOnly = true)
    public Optional<Report> byReference(String reference) {
        return reports.findByReference(reference);
    }

    public Department triageDepartment() {
        return departments.findByCode("TRIAGE").orElseThrow();
    }

    public int duplicateRadiusMeters() {
        return props.duplicateRadiusMeters();
    }
}
