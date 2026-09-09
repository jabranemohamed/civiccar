package tn.civiccare.observability;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tn.civiccare.notifications.OutboxMessage;
import tn.civiccare.notifications.OutboxRepository;
import tn.civiccare.reports.ReportRepository;
import tn.civiccare.reports.WorkflowStatus;

import java.util.List;

/**
 * Rafraîchissement périodique des jauges métier (backlog outbox, dossiers actifs).
 * Mesures agrégées à intervalle borné : la base n'est jamais requêtée à chaque scrape.
 */
@Component
public class ObservabilityJobs {

    private final Telemetry telemetry;
    private final OutboxRepository outbox;
    private final ReportRepository reports;

    public ObservabilityJobs(Telemetry telemetry, OutboxRepository outbox, ReportRepository reports) {
        this.telemetry = telemetry;
        this.outbox = outbox;
        this.reports = reports;
    }

    @Scheduled(fixedDelayString = "${civiccare.gauges.refresh-ms:60000}", initialDelay = 10000)
    public void refreshGauges() {
        telemetry.updateBacklog(
                outbox.countByStatus(OutboxMessage.Status.PENDING),
                reports.countByWorkflowStatusIn(List.of(WorkflowStatus.OPEN, WorkflowStatus.IN_PROGRESS)));
    }
}
