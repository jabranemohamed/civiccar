package tn.civiccare.reports;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.subscriptions.SubscriptionService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A13 : archivage à 30 jours et purge à 90 jours avec horloge injectable ;
 * une réouverture décale les échéances ; un abonnement actif indépendant
 * (autre dossier) n'est pas perdu.
 */
class RetentionIT extends AbstractIntegrationTest {

    @Autowired
    ReportService reports;
    @Autowired
    RetentionJobs retention;
    @Autowired
    WorkflowService workflow;
    @Autowired
    CatalogService catalog;
    @Autowired
    SubscriptionService subscriptions;
    @Autowired
    MutableClock clock;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired
    jakarta.persistence.EntityManagerFactory emf;

    @AfterEach
    void resetClock() {
        clock.reset();
    }

    private Report createAndClose() {
        Report report = reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId(), 10.1815, 36.7995,
                null, null, "Rétention.", Map.of(), "ret-" + UUID.randomUUID() + "@example.com",
                null, true, UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        template.executeWithoutResult(tx -> {
            var r = reports.byReference(report.getReference()).orElseThrow();
            r.setWorkflowStatus(WorkflowStatus.DONE_OR_ORDERED);
            r.setClosedAt(clock.instant());
        });
        return report;
    }

    private Report reload(Report report) {
        return reports.byReference(report.getReference()).orElseThrow();
    }

    @Test
    void archiveAfter30DaysAndPurgeAfter90Days() {
        Instant start = Instant.parse("2026-01-10T10:00:00Z");
        clock.set(start);
        Report report = createAndClose();

        clock.set(start.plus(Duration.ofDays(29)));
        retention.archiveDue();
        assertThat(reload(report).getArchivedAt()).as("pas encore archivable à J+29").isNull();

        clock.set(start.plus(Duration.ofDays(31)));
        retention.archiveDue();
        assertThat(reload(report).getArchivedAt()).isNotNull();

        clock.set(start.plus(Duration.ofDays(89)));
        retention.purgeDue();
        assertThat(reload(report).getPersonalDataPurgedAt()).isNull();

        clock.set(start.plus(Duration.ofDays(91)));
        retention.purgeDue();
        Report purged = reload(report);
        assertThat(purged.getPersonalDataPurgedAt()).isNotNull();
        assertThat(purged.getDescriptionPrivate()).as("texte source privé purgé").isNull();

        // Contact effacé
        try (var em = emf.createEntityManager()) {
            ReportContact contact = em.find(ReportContact.class, report.getId());
            assertThat(contact.isPurged()).isTrue();
            assertThat(contact.getEmail()).isNull();
            assertThat(contact.getPhone()).isNull();
        }
    }

    @Test
    void reopeningClearsDeadlinesWithoutLosingHistory() {
        Instant start = Instant.parse("2026-02-01T10:00:00Z");
        clock.set(start);
        Report report = createAndClose();

        clock.set(start.plus(Duration.ofDays(31)));
        retention.archiveDue();
        assertThat(reload(report).getArchivedAt()).isNotNull();

        // Réouverture par un admin (rôle privilégié) : échéances effacées
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(
                        "admin", null, "ROLE_ADMIN"));
        try {
            workflow.changeStatus("admin", report.getId(), WorkflowStatus.IN_PROGRESS, "réouverture test");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        Report reopened = reload(report);
        assertThat(reopened.getClosedAt()).isNull();
        assertThat(reopened.getArchivedAt()).isNull();

        // Plus jamais purgé tant que non re-clos
        clock.set(start.plus(Duration.ofDays(200)));
        retention.purgeDue();
        assertThat(reload(report).getPersonalDataPurgedAt()).isNull();
    }

    @Test
    void purgeDoesNotTouchIndependentSubscriptionOnAnotherReport() {
        Instant start = Instant.parse("2026-03-01T10:00:00Z");
        clock.set(start);
        Report purgedReport = createAndClose();
        Report otherReport = reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("BENCH_DIRTY").orElseThrow().getId(), 10.1800, 36.8000,
                null, null, "Autre dossier.", Map.of(), "meme-adresse@example.com",
                null, true, UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));

        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        // Même adresse abonnée aux deux dossiers, activée sur les deux
        template.executeWithoutResult(tx -> {
            subscriptions.requestSubscription(purgedReport.getId(), "meme-adresse@example.com");
            subscriptions.requestSubscription(otherReport.getId(), "meme-adresse@example.com");
        });
        try (var em = emf.createEntityManager()) {
            var subs = em.createQuery("select s from Subscription s where s.email = :e",
                            tn.civiccare.subscriptions.Subscription.class)
                    .setParameter("e", "meme-adresse@example.com").getResultList();
            var tx2 = em.getTransaction();
            tx2.begin();
            subs.forEach(s -> em.merge(s).activate(clock.instant()));
            tx2.commit();
        }

        clock.set(start.plus(Duration.ofDays(91)));
        retention.purgeDue();

        try (var em = emf.createEntityManager()) {
            var purgedSubs = em.createQuery(
                            "select s from Subscription s where s.reportId = :r",
                            tn.civiccare.subscriptions.Subscription.class)
                    .setParameter("r", purgedReport.getId()).getResultList();
            assertThat(purgedSubs).as("abonnements du dossier purgé supprimés").isEmpty();

            var independentSub = em.createQuery(
                            "select s from Subscription s where s.reportId = :r",
                            tn.civiccare.subscriptions.Subscription.class)
                    .setParameter("r", otherReport.getId()).getSingleResult();
            assertThat(independentSub.getEmail())
                    .as("abonnement actif indépendant conservé")
                    .isEqualTo("meme-adresse@example.com");
            assertThat(independentSub.getStatus())
                    .isEqualTo(tn.civiccare.subscriptions.Subscription.Status.ACTIVE);
        }
    }
}
