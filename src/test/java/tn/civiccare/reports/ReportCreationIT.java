package tn.civiccare.reports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.media.ImageProcessor;
import tn.civiccare.media.MediaService;
import tn.civiccare.notifications.OutboxRepository;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.ReportService.CreateReportCommand;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A03/A04/A05/A06 : création sans compte, validations serveur (photos, description,
 * e-mail, téléphone, consentement, périmètre, champs conditionnels), idempotence.
 */
class ReportCreationIT extends AbstractIntegrationTest {

    // Point public à Tunis (avenue Habib Bourguiba), dans le périmètre de démonstration
    static final double LON_TUNIS = 10.1815;
    static final double LAT_TUNIS = 36.7995;
    // Le Bardo : en Tunisie mais hors du périmètre municipal de démonstration
    static final double LON_BARDO = 10.1345;
    static final double LAT_BARDO = 36.8093;

    @Autowired
    ReportService reports;
    @Autowired
    CatalogService catalog;
    @Autowired
    MediaService media;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    tn.civiccare.geo.BoundaryService boundary;

    private ServiceType trashBin() {
        return catalog.typeByCode("TRASH_BIN_FULL").orElseThrow();
    }

    private CreateReportCommand valid(String idempotencyKey) {
        return new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                "Avenue Habib Bourguiba, Tunis", null, "Poubelle pleine depuis deux jours.",
                Map.of(), "test-" + UUID.randomUUID() + "@example.com", null, true,
                idempotencyKey, Locale.forLanguageTag("fr"), List.of());
    }

    static byte[] jpeg(int w, int h) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void createWithoutPhotosSucceedsAndQueuesAcknowledgementEmail() {
        Report report = reports.create(valid(UUID.randomUUID().toString()));
        assertThat(report.getReference()).matches("\\d{6}-\\d{4}");
        assertThat(report.getWorkflowStatus()).isEqualTo(WorkflowStatus.OPEN);
        assertThat(report.getPublicationStatus()).isEqualTo(PublicationStatus.PENDING_REVIEW);
        assertThat(report.getDepartment()).isNotNull();
        assertThat(outbox.existsByDedupKey("report.created:" + report.getId())).isTrue();
    }

    @Test
    void createWithThreePhotosSucceedsButFourthIsRejected() {
        List<ImageProcessor.Processed> three = List.of(
                media.validateAndProcess(jpeg(600, 400)),
                media.validateAndProcess(jpeg(500, 500)),
                media.validateAndProcess(jpeg(400, 600)));
        CreateReportCommand cmd = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Avec trois photos.", Map.of(), "photos@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), three);
        Report report = reports.create(cmd);
        assertThat(media.allFor(report.getId())).hasSize(3);
        // Toutes en attente de modération, aucune publique
        assertThat(media.approvedFor(report.getId())).isEmpty();

        List<ImageProcessor.Processed> four = List.of(three.get(0), three.get(1), three.get(2),
                media.validateAndProcess(jpeg(300, 300)));
        CreateReportCommand tooMany = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Quatre photos.", Map.of(), "four@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), four);
        assertThatThrownBy(() -> reports.create(tooMany))
                .isInstanceOf(ValidationException.class)
                .hasMessage("media.tooMany");
    }

    @Test
    void invalidImageFormatAndOversizeAreRejectedServerSide() {
        // SVG / contenu actif refusé par la liste de signatures
        assertThatThrownBy(() -> media.validateAndProcess("<svg xmlns='x'></svg>".getBytes()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("media.format");
        // Taille excessive (12 Mo de bruit)
        byte[] tooBig = new byte[12 * 1024 * 1024];
        tooBig[0] = (byte) 0xFF;
        tooBig[1] = (byte) 0xD8;
        tooBig[2] = (byte) 0xFF;
        assertThatThrownBy(() -> media.validateAndProcess(tooBig))
                .isInstanceOf(ValidationException.class)
                .hasMessage("media.tooLarge");
    }

    @Test
    void descriptionRequiredAndLimitedTo300Characters() {
        CreateReportCommand empty = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "  ", Map.of(), "d@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(empty)).hasMessage("description.invalid");

        CreateReportCommand tooLong = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "x".repeat(301), Map.of(), "d@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(tooLong)).hasMessage("description.invalid");
    }

    @Test
    void emailAndConsentRequiredPhoneOptionalButValidated() {
        CreateReportCommand badEmail = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of(), "pas-un-email", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(badEmail)).hasMessage("email.invalid");

        CreateReportCommand noConsent = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of(), "ok@example.com", null, false,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(noConsent)).hasMessage("consent.required");

        // Téléphone tunisien local normalisé en E.164, international accepté, invalide refusé
        CreateReportCommand tnPhone = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of(), "tn@example.com", "71 234 567", true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThat(reports.create(tnPhone)).isNotNull();

        CreateReportCommand intlPhone = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of(), "intl@example.com", "+33 6 12 34 56 78", true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThat(reports.create(intlPhone)).isNotNull();

        CreateReportCommand badPhone = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of(), "bad@example.com", "123", true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(badPhone)).hasMessage("phone.invalid");
    }

    @Test
    void boundaryValidation_insideOutsideAndOnTheEdge() {
        // Intérieur de Tunis : accepté (vérifié dans createWithoutPhotos)
        assertThat(boundary.isInsideBoundary(LON_TUNIS, LAT_TUNIS)).isTrue();
        // Le Bardo : en Tunisie mais hors municipalité -> refusé
        assertThat(boundary.isInsideBoundary(LON_BARDO, LAT_BARDO)).isFalse();
        CreateReportCommand bardo = new CreateReportCommand(trashBin().getId(), LON_BARDO, LAT_BARDO,
                null, null, "Hors périmètre.", Map.of(), "b@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(bardo)).hasMessage("position.outside");
        // Point exactement sur la limite : ST_Covers => considéré À L'INTÉRIEUR (choix documenté).
        // Sommet du polygone de démonstration : (10.150, 36.845)
        assertThat(boundary.isInsideBoundary(10.150, 36.845)).isTrue();
        // Ariana et Ben Arous : hors périmètre
        assertThat(boundary.isInsideBoundary(10.1937, 36.8665)).isFalse();
        assertThat(boundary.isInsideBoundary(10.2189, 36.7435)).isFalse();
    }

    @Test
    void doubleSubmitWithSameIdempotencyKeyCreatesOneReport() {
        String key = UUID.randomUUID().toString();
        Report first = reports.create(valid(key));
        Report second = reports.create(valid(key));
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getReference()).isEqualTo(first.getReference());
    }

    @Test
    void posterRequiresPartyFromCatalog_andPlateStaysPrivate() {
        ServiceType poster = catalog.typeByCode("POSTER_DAMAGED").orElseThrow();
        CreateReportCommand noParty = new CreateReportCommand(poster.getId(), LON_TUNIS, LAT_TUNIS,
                null, null, null, Map.of(), "p@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(noParty))
                .hasMessage("field.required:POLITICAL_PARTY");

        CreateReportCommand badOption = new CreateReportCommand(poster.getId(), LON_TUNIS, LAT_TUNIS,
                null, null, null, Map.of("POLITICAL_PARTY", "PARTI_REEL"), "p@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(badOption))
                .hasMessage("field.invalidOption:POLITICAL_PARTY");

        Report ok = reports.create(new CreateReportCommand(poster.getId(), LON_TUNIS, LAT_TUNIS,
                null, null, null, Map.of("POLITICAL_PARTY", "PARTY_ALPHA"), "p@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
        assertThat(ok.getFieldValues()).containsEntry("POLITICAL_PARTY", "PARTY_ALPHA");
        // Description standard désactivée pour les affiches : dossier créé sans description
        assertThat(ok.getDescriptionPublic()).isNull();

        // Véhicule hors d'usage : la plaque va dans le contact privé, pas dans les valeurs du dossier
        ServiceType vehicle = catalog.typeByCode("ROAD_ABANDONED_VEHICLE").orElseThrow();
        Report vehicleReport = reports.create(new CreateReportCommand(vehicle.getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Voiture abandonnée.", Map.of("VEHICLE_PLATE", "123 TU 4567"),
                "v@example.com", null, true, UUID.randomUUID().toString(),
                Locale.forLanguageTag("fr"), List.of()));
        assertThat(vehicleReport.getFieldValues()).doesNotContainKey("VEHICLE_PLATE");
    }

    @Test
    void unknownFieldKeysAreRejected() {
        CreateReportCommand unknown = new CreateReportCommand(trashBin().getId(), LON_TUNIS, LAT_TUNIS,
                null, null, "Desc.", Map.of("INJECTED_FIELD", "x"), "u@example.com", null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of());
        assertThatThrownBy(() -> reports.create(unknown)).hasMessage("field.unknown:INJECTED_FIELD");
    }
}
