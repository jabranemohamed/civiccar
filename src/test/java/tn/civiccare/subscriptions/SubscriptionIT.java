package tn.civiccare.subscriptions;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.notifications.OutboxDispatcher;
import tn.civiccare.notifications.OutboxMessage;
import tn.civiccare.notifications.OutboxRepository;
import tn.civiccare.reports.Report;
import tn.civiccare.reports.ReportService;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A10/A16 : favoris sans compte ; aucun e-mail de statut avant activation ;
 * jetons expirés/rejoués correctement traités ; panne SMTP -> création conservée + reprise outbox.
 */
class SubscriptionIT extends AbstractIntegrationTest {

    @Autowired
    SubscriptionService subscriptions;
    @Autowired
    ReportService reports;
    @Autowired
    CatalogService catalog;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    RecordingMailSender mailSender;
    @Autowired
    MutableClock clock;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;

    private Report createReport(String email) {
        return reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("BENCH_DAMAGED").orElseThrow().getId(), 10.1815, 36.7995,
                null, null, "Banc cassé.", Map.of(), email, null, true,
                UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
    }

    @Test
    void bookmarksWorkPerDeviceWithoutAccount() {
        var binding = subscriptions.resolveBrowser(null);
        assertThat(binding.created()).isTrue();
        Report report = createReport("bm@example.com");

        assertThat(subscriptions.toggleBookmark(binding.browserId(), report.getId())).isTrue();
        assertThat(subscriptions.isBookmarked(binding.browserId(), report.getId())).isTrue();
        assertThat(subscriptions.bookmarkedReportIds(binding.browserId())).contains(report.getId());

        // Même jeton -> même identité (persistance du cookie)
        var again = subscriptions.resolveBrowser(binding.rawToken());
        assertThat(again.browserId()).isEqualTo(binding.browserId());
        assertThat(again.created()).isFalse();

        assertThat(subscriptions.toggleBookmark(binding.browserId(), report.getId())).isFalse();
        assertThat(subscriptions.isBookmarked(binding.browserId(), report.getId())).isFalse();
    }

    @Test
    void subscriptionTokenLifecycle_confirmExpireReplay() {
        clock.reset();
        Report report = createReport("cycle@example.com");
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);

        String url = template.execute(tx ->
                subscriptions.requestSubscription(report.getId(), "cycle@example.com"));
        String token = url.substring(url.lastIndexOf('/') + 1);

        // Jeton inconnu rejeté
        assertThat(subscriptions.confirm("jeton-bidon")).isFalse();
        // Activation
        assertThat(subscriptions.confirm(token)).isTrue();
        // Rejeu : usage unique
        assertThat(subscriptions.confirm(token)).isFalse();

        template.executeWithoutResult(tx ->
                assertThat(subscriptions.activeSubscribers(report.getId()))
                        .hasSize(1)
                        .first()
                        .satisfies(s -> assertThat(s.email()).isEqualTo("cycle@example.com")));

        // Jeton expiré (8 jours après création, TTL 7 jours)
        String url2 = template.execute(tx ->
                subscriptions.requestSubscription(report.getId(), "autre@example.com"));
        String token2 = url2.substring(url2.lastIndexOf('/') + 1);
        clock.advance(Duration.ofDays(8));
        assertThat(subscriptions.confirm(token2)).as("jeton expiré rejeté").isFalse();
        clock.reset();
    }

    @Test
    void noStatusEmailBeforeActivation_unsubscribeStopsEmails() {
        Report report = createReport("silence@example.com");
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        // Abonnement PENDING créé au dépôt : aucun abonné actif
        template.executeWithoutResult(tx ->
                assertThat(subscriptions.activeSubscribers(report.getId())).isEmpty());

        // Après activation, l'abonné apparaît ; après désinscription, il disparaît
        String url = template.execute(tx ->
                subscriptions.requestSubscription(report.getId(), "silence@example.com"));
        String token = url.substring(url.lastIndexOf('/') + 1);
        subscriptions.confirm(token);

        java.util.concurrent.atomic.AtomicReference<String> unsubscribeUrl = new java.util.concurrent.atomic.AtomicReference<>();
        template.executeWithoutResult(tx -> {
            var subs = subscriptions.activeSubscribers(report.getId());
            assertThat(subs).hasSize(1);
            unsubscribeUrl.set(subs.getFirst().unsubscribeUrl());
        });
        String unsubToken = unsubscribeUrl.get().substring(unsubscribeUrl.get().lastIndexOf('/') + 1);
        assertThat(subscriptions.unsubscribe(unsubToken)).isTrue();
        assertThat(subscriptions.unsubscribe(unsubToken)).as("rejeu sans effet").isFalse();
        template.executeWithoutResult(tx ->
                assertThat(subscriptions.activeSubscribers(report.getId())).isEmpty());
    }

    @Test
    void smtpOutage_reportIsCreatedAndOutboxRetriesLater() {
        clock.reset();
        mailSender.sent.clear();
        mailSender.failNext = true;
        Report report = createReport("panne-smtp@example.com");
        assertThat(reports.byReference(report.getReference()))
                .as("création conservée malgré la panne SMTP").isPresent();

        // Premier passage : échec, message replanifié
        dispatcher.dispatchDue();
        OutboxMessage msg = outbox.findAll().stream()
                .filter(m -> m.getKind().equals("report.created")
                        && "panne-smtp@example.com".equals(m.getPayload().get("to")))
                .findFirst().orElseThrow();
        assertThat(msg.getStatus()).isEqualTo(OutboxMessage.Status.PENDING);
        assertThat(msg.getAttempts()).isGreaterThanOrEqualTo(1);

        // SMTP rétabli : reprise après le délai de backoff
        mailSender.failNext = false;
        clock.advance(Duration.ofHours(2));
        dispatcher.dispatchDue();
        assertThat(mailSender.sent)
                .anyMatch(m -> m.getTo() != null && m.getTo()[0].equals("panne-smtp@example.com"));
        clock.reset();
    }
}
