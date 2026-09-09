package tn.civiccare.administration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.geo.GeoPoints;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserRepository;
import tn.civiccare.media.ImageProcessor;
import tn.civiccare.media.MediaService;
import tn.civiccare.reports.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Jeu de démonstration déterministe : ~60 dossiers fictifs géolocalisés dans le périmètre
 * de Tunis configuré, tous statuts et états de publication, candidats doublons, historiques,
 * descriptions françaises, arabes et mixtes. Aucune donnée personnelle réelle (adresses
 * e-mail example.com, incidents fictifs). Jamais activé en production (garde dédiée).
 */
@Component
@ConditionalOnProperty("civiccare.demo.seed-enabled")
@Order(10)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ReportRepository reports;
    private final CatalogService catalog;
    private final BoundaryService boundary;
    private final StaffUserRepository staffUsers;
    private final MediaService media;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final jakarta.persistence.EntityManager em;

    public DemoDataSeeder(ReportRepository reports, CatalogService catalog, BoundaryService boundary,
                          StaffUserRepository staffUsers, MediaService media,
                          PlatformTransactionManager txManager, Clock clock,
                          jakarta.persistence.EntityManager em) {
        this.reports = reports;
        this.catalog = catalog;
        this.boundary = boundary;
        this.staffUsers = staffUsers;
        this.media = media;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.em = em;
    }

    // Lieux publics de la ville de Tunis (repères de démonstration ; incidents fictifs)
    private record Spot(String fr, String ar, double lon, double lat) {
    }

    private static final List<Spot> SPOTS = List.of(
            new Spot("Avenue Habib Bourguiba", "شارع الحبيب بورقيبة", 10.1815, 36.7995),
            new Spot("Médina de Tunis", "المدينة العتيقة", 10.1712, 36.7981),
            new Spot("Bab El Bhar", "باب البحر", 10.1770, 36.7986),
            new Spot("Place de la Kasbah", "ساحة القصبة", 10.1660, 36.7969),
            new Spot("Place Barcelone", "ساحة برشلونة", 10.1826, 36.7952),
            new Spot("Parc du Belvédère", "منتزه البلفيدير", 10.1723, 36.8228),
            new Spot("Bab Souika", "باب سويقة", 10.1667, 36.8055),
            new Spot("Avenue Mohamed V", "شارع محمد الخامس", 10.1866, 36.8058),
            new Spot("El Menzah", "المنزه", 10.1719, 36.8398),
            new Spot("El Omrane", "العمران", 10.1580, 36.8194),
            new Spot("Montfleury", "مونفلوري", 10.1786, 36.7830),
            new Spot("El Kabaria", "الكبارية", 10.1743, 36.7568),
            new Spot("Les Berges du Lac", "ضفاف البحيرة", 10.2331, 36.8320),
            new Spot("Cité El Khadra", "حي الخضراء", 10.1976, 36.8266));

    private static final List<String> DESCRIPTIONS_FR = List.of(
            "Le problème est visible depuis plusieurs jours et gêne le passage.",
            "Situation constatée ce matin, merci d'intervenir rapidement.",
            "Problème signalé par plusieurs riverains du quartier.",
            "L'état s'est dégradé après les dernières pluies.",
            "Gêne importante pour les piétons et les personnes à mobilité réduite.");

    private static final List<String> DESCRIPTIONS_AR = List.of(
            "المشكلة ظاهرة منذ عدة أيام وتعيق المرور.",
            "لاحظنا الوضع هذا الصباح، نرجو التدخل بسرعة.",
            "أبلغ عدة متساكنين عن هذه المشكلة في الحي.",
            "تدهورت الحالة بعد الأمطار الأخيرة.",
            "إزعاج كبير للمشاة ولذوي الاحتياجات الخاصة.");

    private static final List<String> DESCRIPTIONS_MIXED = List.of(
            "Problème récurrent — مشكلة متكررة في نفس المكان.",
            "Vu près de l'arrêt de bus, بالقرب من محطة الحافلات.",
            "Merci d'intervenir — الرجاء التدخل في أقرب وقت.");

    @Override
    public void run(ApplicationArguments args) {
        Long existing = tx.execute(s -> reports.count());
        if (existing != null && existing > 0) {
            log.info("Seed démo ignoré : {} dossiers déjà présents.", existing);
            return;
        }
        tx.executeWithoutResult(s -> seed());
        log.info("Seed de démonstration Tunis créé.");
    }

    private void seed() {
        Random random = new Random(42);
        List<ServiceType> types = catalog.activeTypes();
        StaffUser moderator = staffUsers.findByUsername("moderator").orElse(null);
        Instant now = clock.instant();
        int counter = 0;

        for (int i = 0; i < 60; i++) {
            counter++;
            ServiceType type = types.get(i % types.size());
            Spot spot = SPOTS.get(random.nextInt(SPOTS.size()));
            double lon = 0;
            double lat = 0;
            for (int attempt = 0; attempt < 25; attempt++) {
                lon = spot.lon() + (random.nextDouble() - 0.5) * 0.008;
                lat = spot.lat() + (random.nextDouble() - 0.5) * 0.008;
                if (boundary.isInsideBoundary(lon, lat)) {
                    break;
                }
            }
            Instant createdAt = now.minus(Duration.ofDays(random.nextInt(120)))
                    .minus(Duration.ofMinutes(random.nextInt(1440)));
            int year = createdAt.atZone(java.time.ZoneId.of("Africa/Tunis")).getYear();
            String reference = "%06d-%d".formatted(counter, year);

            Report report = new Report(UUID.randomUUID(), reference, type,
                    GeoPoints.of(lon, lat), createdAt);
            String address = (i % 3 == 1 ? spot.ar() : spot.fr()) + ", Tunis";
            report.setAddress(address);
            String description = switch (i % 6) {
                case 0, 3 -> DESCRIPTIONS_FR.get(random.nextInt(DESCRIPTIONS_FR.size()));
                case 1, 4 -> DESCRIPTIONS_AR.get(random.nextInt(DESCRIPTIONS_AR.size()));
                default -> DESCRIPTIONS_MIXED.get(random.nextInt(DESCRIPTIONS_MIXED.size()));
            };
            if (type.isStandardDescription()) {
                report.setDescriptionPrivate(description);
                report.setDescriptionPublic(description);
            }
            if (type.getCode().startsWith("POSTER_")) {
                report.setFieldValues(Map.of("POLITICAL_PARTY",
                        List.of("PARTY_ALPHA", "PARTY_BETA", "PARTY_GAMMA").get(random.nextInt(3))));
            }
            report.setDepartment(catalog.routeFor(type.getId()).orElse(null));

            // Répartition des statuts : ouverts, en cours, clos récents, clos archivés, hors périmètre
            WorkflowStatus workflow;
            Instant closedAt = null;
            Instant archivedAt = null;
            int bucket = i % 12;
            if (bucket < 4) {
                workflow = WorkflowStatus.OPEN;
            } else if (bucket < 7) {
                workflow = WorkflowStatus.IN_PROGRESS;
            } else if (bucket < 10) {
                workflow = WorkflowStatus.DONE_OR_ORDERED;
                closedAt = createdAt.plus(Duration.ofDays(3 + random.nextInt(10)));
                if (Duration.between(closedAt, now).toDays() > 30) {
                    archivedAt = closedAt.plus(Duration.ofDays(30));
                }
            } else if (bucket < 11) {
                workflow = WorkflowStatus.OUT_OF_SCOPE;
                closedAt = createdAt.plus(Duration.ofDays(1 + random.nextInt(5)));
            } else {
                workflow = WorkflowStatus.OPEN;
            }
            report.setWorkflowStatus(workflow);
            report.setClosedAt(closedAt);
            report.setArchivedAt(archivedAt);

            PublicationStatus publication = i % 10 == 8 ? PublicationStatus.PENDING_REVIEW
                    : i % 10 == 9 ? PublicationStatus.HIDDEN
                    : PublicationStatus.PUBLISHED;
            report.setPublicationStatus(publication);
            report.touch(closedAt != null ? closedAt : createdAt);
            reports.save(report);

            em.persist(new ReportContact(report.getId(), "citoyen" + counter + "@example.com",
                    i % 4 == 0 ? "+2167123456" + (counter % 10) : null,
                    type.getCode().equals("ROAD_ABANDONED_VEHICLE") ? "123 TU " + (1000 + counter) : null,
                    "v1-2026", createdAt));

            em.persist(new StatusEvent(UUID.randomUUID(), report.getId(), null,
                    WorkflowStatus.OPEN, null, null, createdAt));
            if (workflow != WorkflowStatus.OPEN) {
                Instant progressAt = createdAt.plus(Duration.ofHours(6 + random.nextInt(48)));
                em.persist(new StatusEvent(UUID.randomUUID(), report.getId(), WorkflowStatus.OPEN,
                        workflow == WorkflowStatus.OUT_OF_SCOPE ? WorkflowStatus.OUT_OF_SCOPE
                                : WorkflowStatus.IN_PROGRESS,
                        moderator == null ? null : moderator.getId(), null, progressAt));
                if (workflow == WorkflowStatus.DONE_OR_ORDERED) {
                    em.persist(new StatusEvent(UUID.randomUUID(), report.getId(),
                            WorkflowStatus.IN_PROGRESS, WorkflowStatus.DONE_OR_ORDERED,
                            moderator == null ? null : moderator.getId(), null, closedAt));
                    em.persist(new PublicUpdate(UUID.randomUUID(), report.getId(),
                            moderator == null ? null : moderator.getId(),
                            i % 2 == 0 ? "Intervention réalisée ou commandée auprès de l'équipe compétente."
                                    : "تمت المعالجة أو صدر أمر بالتدخل للفريق المختص.",
                            closedAt));
                }
            }

            // Quelques photos synthétiques (images générées, aucune photo réelle)
            if (i % 8 == 0 && publication == PublicationStatus.PUBLISHED) {
                ImageProcessor.Processed processed = media.validateAndProcess(syntheticJpeg(random, reference));
                var asset = media.attach(report.getId(), processed, 0);
                media.setModeration(asset.getId(), tn.civiccare.media.MediaAsset.ModerationStatus.APPROVED);
            }
        }

        // Paires de doublons candidats : même famille, ~25 m d'écart, publiés et actifs
        seedDuplicatePair(types, 61, "TRASH_BIN_FULL", 10.1815, 36.7997, now);
        seedDuplicatePair(types, 63, "STREET_LIGHT_LAMP_OUT", 10.1724, 36.8226, now);
        log.info("Seed : {} dossiers de démonstration.", reports.count());
    }

    private void seedDuplicatePair(List<ServiceType> types, int startCounter, String typeCode,
                                   double lon, double lat, Instant now) {
        ServiceType type = types.stream().filter(t -> t.getCode().equals(typeCode)).findFirst().orElseThrow();
        int year = now.atZone(java.time.ZoneId.of("Africa/Tunis")).getYear();
        for (int j = 0; j < 2; j++) {
            String reference = "%06d-%d".formatted(startCounter + j, year);
            // ~25 m ≈ 0.00022° de latitude
            Report report = new Report(UUID.randomUUID(), reference, type,
                    GeoPoints.of(lon + j * 0.0002, lat + j * 0.00012), now.minus(Duration.ofDays(2 + j)));
            report.setAddress("Avenue Habib Bourguiba, Tunis");
            report.setDescriptionPrivate("Signalement de démonstration pour la détection de doublons.");
            report.setDescriptionPublic("Signalement de démonstration pour la détection de doublons.");
            report.setDepartment(catalog.routeFor(type.getId()).orElse(null));
            report.setPublicationStatus(PublicationStatus.PUBLISHED);
            reports.save(report);
            em.persist(new ReportContact(report.getId(), "citoyen" + (startCounter + j) + "@example.com",
                    null, null, "v1-2026", report.getCreatedAt()));
            em.persist(new StatusEvent(UUID.randomUUID(), report.getId(), null, WorkflowStatus.OPEN,
                    null, null, report.getCreatedAt()));
        }
    }

    /** Petite image JPEG générée (dégradé + référence) : aucun contenu réel. */
    private static byte[] syntheticJpeg(Random random, String reference) {
        int w = 800;
        int h = 600;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        int base = 60 + random.nextInt(120);
        for (int y = 0; y < h; y++) {
            g.setColor(new java.awt.Color(base, 90 + (y * 100 / h), 140 - (y * 60 / h)));
            g.fillRect(0, y, w, 1);
        }
        g.setColor(java.awt.Color.WHITE);
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 40));
        g.drawString("Démo " + reference, 40, h / 2);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
