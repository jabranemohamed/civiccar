package tn.civiccare.reports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.reports.ReportService.CreateReportCommand;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A07/A08/A09 : doublons candidats sans divulgation des dossiers cachés ;
 * visibilité identique liste/carte ; fiche publique sans données privées.
 */
class VisibilityAndDuplicatesIT extends AbstractIntegrationTest {

    @Autowired
    ReportService reports;
    @Autowired
    ReportQueryService queries;
    @Autowired
    PublicReportFacade facade;
    @Autowired
    CatalogService catalog;
    @Autowired
    tn.civiccare.moderation.ModerationService moderation;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    private Report create(String typeCode, double lon, double lat, String email) {
        return reports.create(new CreateReportCommand(
                catalog.typeByCode(typeCode).orElseThrow().getId(), lon, lat,
                "Avenue Habib Bourguiba", null,
                catalog.typeByCode(typeCode).orElseThrow().isStandardDescription() ? "Test visibilité." : null,
                typeCode.startsWith("POSTER_") ? Map.of("POLITICAL_PARTY", "PARTY_ALPHA") : Map.of(),
                email, null, true, UUID.randomUUID().toString(),
                Locale.forLanguageTag("fr"), List.of()));
    }

    /** Publication directe pour les tests (le service de modération exige un rôle). */
    private void publish(Report report) {
        new org.springframework.transaction.support.TransactionTemplate(txManager)
                .executeWithoutResult(tx -> reports.byReference(report.getReference())
                        .orElseThrow().setPublicationStatus(PublicationStatus.PUBLISHED));
    }

    @Test
    void duplicateCandidatesFoundWithinRadius_sameFamilyOnly_publishedOnly() {
        double lon = 10.1900;
        double lat = 36.8000;
        Report published = create("TRASH_BIN_FULL", lon, lat, "dup1@example.com");
        publish(published);
        // Non publié, même endroit : ne doit JAMAIS apparaître comme candidat
        create("TRASH_BIN_FULL", lon + 0.0001, lat, "hidden@example.com");

        var candidates = queries.findDuplicateCandidates(
                catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId(), lon + 0.0002, lat);
        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().summary().reference()).isEqualTo(published.getReference());
        assertThat(candidates.getFirst().distanceMeters()).isLessThan(50);

        // À 200 m : hors rayon de 50 m
        var far = queries.findDuplicateCandidates(
                catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId(), lon + 0.0025, lat);
        assertThat(far).isEmpty();

        // Autre famille au même endroit : non candidat
        var otherFamily = queries.findDuplicateCandidates(
                catalog.typeByCode("BENCH_DAMAGED").orElseThrow().getId(), lon, lat);
        assertThat(otherFamily).noneMatch(c -> c.summary().reference().equals(published.getReference()));
    }

    @Test
    void unpublishedReportIsInvisibleEverywhere() {
        Report hidden = create("BENCH_DIRTY", 10.1700, 36.7900, "invisible@example.com");
        // Non publié : ni fiche publique, ni liste, ni carte
        assertThat(facade.byReference(hidden.getReference(), Locale.forLanguageTag("fr"))).isEmpty();
        assertThat(queries.publicByReference(hidden.getReference())).isEmpty();
        var list = queries.search(new ReportQueryService.SearchCriteria(
                hidden.getReference(), null, null, null, null, null, false, false), 0, 10);
        assertThat(list).isEmpty();
        var map = queries.mapPoints(10.0, 36.6, 10.4, 37.0, ReportQueryService.SearchCriteria.empty());
        assertThat(map).noneMatch(p -> p.reference().equals(hidden.getReference()));
    }

    @Test
    void publicDetailNeverExposesPrivateData() {
        Report report = create("ROAD_ABANDONED_VEHICLE", 10.1750, 36.7950, "prive@example.com");
        publish(report);
        var detail = facade.byReference(report.getReference(), Locale.forLanguageTag("fr")).orElseThrow();
        String serialized = detail.toString();
        assertThat(serialized).doesNotContain("prive@example.com");
        // La plaque n'est pas dans les champs publics
        assertThat(detail.publicFieldLabels()).isEmpty();
        // Médias non approuvés exclus
        assertThat(detail.mediaKeys()).isEmpty();
    }

    @Test
    void archivedReportsExcludedByDefaultButAvailableWithExplicitFilter() {
        Report report = create("TRASH_BIN_FULL", 10.2000, 36.8100, "arch@example.com");
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        template.executeWithoutResult(tx -> {
            var r = reports.byReference(report.getReference()).orElseThrow();
            r.setPublicationStatus(PublicationStatus.PUBLISHED);
            r.setWorkflowStatus(WorkflowStatus.DONE_OR_ORDERED);
            r.setClosedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(40)));
            r.setArchivedAt(java.time.Instant.now().minus(java.time.Duration.ofDays(10)));
        });
        var without = queries.search(new ReportQueryService.SearchCriteria(
                report.getReference(), null, null, null, null, null, false, false), 0, 10);
        assertThat(without).isEmpty();
        var with = queries.search(new ReportQueryService.SearchCriteria(
                report.getReference(), null, null, null, null, null, true, false), 0, 10);
        assertThat(with).hasSize(1);
        assertThat(with.getFirst().archived()).isTrue();
    }

    @Test
    void unicodeSearchFindsArabicAndFrenchDescriptions() {
        Report arabic = create("PARK_DIRTY", 10.1780, 36.8210, "ar@example.com");
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        template.executeWithoutResult(tx -> {
            var r = reports.byReference(arabic.getReference()).orElseThrow();
            r.setDescriptionPublic("المنتزه متسخ جداً قرب المدخل");
            r.setPublicationStatus(PublicationStatus.PUBLISHED);
        });
        var found = queries.search(new ReportQueryService.SearchCriteria(
                "المنتزه", null, null, null, null, null, false, false), 0, 10);
        assertThat(found).anyMatch(s -> s.reference().equals(arabic.getReference()));

        // Recherche française insensible aux accents
        Report french = create("PARK_DAMAGED", 10.1782, 36.8212, "fr@example.com");
        template.executeWithoutResult(tx -> {
            var r = reports.byReference(french.getReference()).orElseThrow();
            r.setDescriptionPublic("Équipement dégradé côté est");
            r.setPublicationStatus(PublicationStatus.PUBLISHED);
        });
        var accentless = queries.search(new ReportQueryService.SearchCriteria(
                "equipement degrade", null, null, null, null, null, false, false), 0, 10);
        assertThat(accentless).anyMatch(s -> s.reference().equals(french.getReference()));
    }
}
