package tn.civiccare.reports;

import io.opentelemetry.api.common.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.subscriptions.SubscriptionService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Jobs de conservation. Délais 30/90 jours : choix produit configurables repris des textes
 * du site de référence, pas des obligations légales universelles. Horloge injectable (tests A13).
 */
@Component
public class RetentionJobs {

    private static final Logger log = LoggerFactory.getLogger(RetentionJobs.class);

    private final ReportRepository reports;
    private final ReportContactRepository contacts;
    private final SubscriptionService subscriptions;
    private final AppProperties props;
    private final Clock clock;
    private final Telemetry telemetry;

    public RetentionJobs(ReportRepository reports, ReportContactRepository contacts,
                         SubscriptionService subscriptions, AppProperties props,
                         Clock clock, Telemetry telemetry) {
        this.reports = reports;
        this.contacts = contacts;
        this.subscriptions = subscriptions;
        this.props = props;
        this.clock = clock;
        this.telemetry = telemetry;
    }

    @Scheduled(cron = "${civiccare.retention.cron:0 15 3 * * *}", zone = "${civiccare.timezone:Africa/Tunis}")
    public void nightly() {
        archiveDue();
        purgeDue();
    }

    /** Archive les dossiers toujours clos depuis plus de N jours. Une réouverture a effacé closedAt. */
    @Transactional
    public int archiveDue() {
        return telemetry.span("report.archive", Attributes.empty(), () -> {
            Instant now = clock.instant();
            Instant threshold = now.minus(Duration.ofDays(props.archiveAfterDays()));
            List<Report> due = reports.findArchivable(threshold);
            for (Report report : due) {
                report.setArchivedAt(now);
                report.touch(now);
            }
            if (!due.isEmpty()) {
                log.info("Archivage : {} dossier(s)", due.size());
            }
            return due.size();
        });
    }

    /**
     * Purge des données personnelles N jours après clôture toujours effective :
     * contact (e-mail, téléphone, plaque), abonnements et jetons du dossier,
     * texte source privé. Les contenus publics conservés restent expurgés.
     * Un abonnement actif indépendant sur un AUTRE dossier n'est pas touché.
     */
    @Transactional
    public int purgeDue() {
        return telemetry.span("privacy.purge", Attributes.empty(), () -> {
            Instant now = clock.instant();
            Instant threshold = now.minus(Duration.ofDays(props.purgeAfterDays()));
            List<Report> due = reports.findPurgeable(threshold);
            for (Report report : due) {
                contacts.findById(report.getId()).ifPresent(ReportContact::purge);
                subscriptions.purgeForReport(report.getId());
                report.setDescriptionPrivate(null);
                report.setPersonalDataPurgedAt(now);
                report.touch(now);
            }
            if (!due.isEmpty()) {
                log.info("Purge données personnelles : {} dossier(s)", due.size());
            }
            return due.size();
        });
    }
}
