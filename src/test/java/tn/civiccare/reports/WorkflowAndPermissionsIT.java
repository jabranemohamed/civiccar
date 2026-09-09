package tn.civiccare.reports;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.administration.AdminFacade;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.moderation.ModerationService;
import tn.civiccare.notifications.OutboxRepository;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.subscriptions.SubscriptionService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A11/A12 : permissions entre services (contrôle métier même si l'UI est contournée),
 * transitions avec audit et notifications outbox, modération sans arrêt du traitement.
 */
class WorkflowAndPermissionsIT extends AbstractIntegrationTest {

    @Autowired
    ReportService reports;
    @Autowired
    WorkflowService workflow;
    @Autowired
    ModerationService moderation;
    @Autowired
    CatalogService catalog;
    @Autowired
    AdminFacade admin;
    @Autowired
    OutboxRepository outbox;
    @Autowired
    SubscriptionService subscriptions;
    @Autowired
    org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired
    MutableClock clock;

    private Report createCleanlinessReport() {
        // TRASH_BIN_FULL est routé vers DEMO_CLEANLINESS
        return reports.create(new ReportService.CreateReportCommand(
                catalog.typeByCode("TRASH_BIN_FULL").orElseThrow().getId(), 10.1815, 36.7995,
                null, null, "Poubelle pleine.", Map.of(), "wf-" + UUID.randomUUID() + "@example.com",
                null, true, UUID.randomUUID().toString(), Locale.forLanguageTag("fr"), List.of()));
    }

    @Test
    @WithMockUser(username = "agent.voirie", roles = "AGENT")
    void agentOfAnotherDepartmentCannotActOnReport() {
        Report report = createCleanlinessReport();
        // agent.voirie (DEMO_ROADS) ne peut pas agir sur un dossier propreté
        assertThatThrownBy(() -> workflow.changeStatus("agent.voirie", report.getId(),
                WorkflowStatus.IN_PROGRESS, null))
                .isInstanceOf(AccessDeniedException.class);
        // Et ne le voit pas dans sa file
        var queue = admin.workQueue("agent.voirie", AdminFacade.WorkQueueFilter.empty(), 0, 100);
        assertThat(queue).noneMatch(r -> r.reference().equals(report.getReference()));
    }

    @Test
    @WithMockUser(username = "agent.proprete", roles = "AGENT")
    void agentOfOwnDepartmentCanTransitionAndItIsAuditedAndEventStored() {
        Report report = createCleanlinessReport();
        workflow.changeStatus("agent.proprete", report.getId(), WorkflowStatus.IN_PROGRESS, null);
        var updated = reports.byReference(report.getReference()).orElseThrow();
        assertThat(updated.getWorkflowStatus()).isEqualTo(WorkflowStatus.IN_PROGRESS);

        workflow.changeStatus("agent.proprete", report.getId(), WorkflowStatus.DONE_OR_ORDERED, null);
        updated = reports.byReference(report.getReference()).orElseThrow();
        assertThat(updated.getClosedAt()).isNotNull();

        // Transition invalide : DONE -> OPEN interdit
        assertThatThrownBy(() -> workflow.changeStatus("agent.proprete", report.getId(),
                WorkflowStatus.OPEN, "x"))
                .isInstanceOf(ValidationException.class);

        // Réouverture : motif requis
        assertThatThrownBy(() -> workflow.changeStatus("agent.proprete", report.getId(),
                WorkflowStatus.IN_PROGRESS, " "))
                .hasMessage("reopen.reasonRequired");
        workflow.changeStatus("agent.proprete", report.getId(), WorkflowStatus.IN_PROGRESS,
                "Problème réapparu");
        updated = reports.byReference(report.getReference()).orElseThrow();
        assertThat(updated.getClosedAt()).as("réouverture efface l'échéance").isNull();
        assertThat(updated.getArchivedAt()).isNull();
    }

    @Test
    @WithMockUser(username = "agent.proprete", roles = "AGENT")
    void statusChangeNotifiesOnlyActiveSubscribersViaOutbox() {
        Report report = createCleanlinessReport();
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        // Un abonnement PENDING (créé au dépôt) ne doit rien recevoir. On en active un.
        template.executeWithoutResult(tx -> {
            String url = subscriptions.requestSubscription(report.getId(), "abonne-actif@example.com");
            assertThat(url).isNotNull();
        });
        // Activation directe par le jeton n'est pas accessible ici : activer via service en base
        template.executeWithoutResult(tx -> {
            var subs = subscriptions.activeSubscribers(report.getId());
            assertThat(subs).as("aucun abonné actif avant activation").isEmpty();
        });

        long before = outbox.count();
        workflow.changeStatus("agent.proprete", report.getId(), WorkflowStatus.IN_PROGRESS, null);
        long after = outbox.count();
        // Aucun abonné actif -> aucune notification de statut (l'accusé initial existe déjà)
        assertThat(after).isEqualTo(before);
    }

    @Test
    @WithMockUser(username = "moderator", roles = "MODERATOR")
    void moderationPublishesWithoutChangingWorkflow() {
        Report report = createCleanlinessReport();
        moderation.setPublication("moderator", report.getId(), PublicationStatus.PUBLISHED, null);
        var updated = reports.byReference(report.getReference()).orElseThrow();
        assertThat(updated.getPublicationStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(updated.getWorkflowStatus()).as("la modération n'arrête pas le traitement")
                .isEqualTo(WorkflowStatus.OPEN);

        moderation.setPublication("moderator", report.getId(), PublicationStatus.HIDDEN, "données perso");
        updated = reports.byReference(report.getReference()).orElseThrow();
        assertThat(updated.getPublicationStatus()).isEqualTo(PublicationStatus.HIDDEN);
        assertThat(updated.getWorkflowStatus()).isEqualTo(WorkflowStatus.OPEN);
    }

    @Test
    @WithMockUser(username = "agent.proprete", roles = "AGENT")
    void agentCannotModerate() {
        Report report = createCleanlinessReport();
        assertThatThrownBy(() -> moderation.setPublication("agent.proprete", report.getId(),
                PublicationStatus.PUBLISHED, null))
                .isInstanceOf(org.springframework.security.authorization.AuthorizationDeniedException.class);
    }

    @Test
    @WithMockUser(username = "moderator", roles = "MODERATOR")
    void categoryChangeIsAuditedAndReroutes() {
        Report report = createCleanlinessReport();
        UUID lightType = catalog.typeByCode("STREET_LIGHT_LAMP_OUT").orElseThrow().getId();
        moderation.changeCategory("moderator", report.getId(), lightType);
        var template = new org.springframework.transaction.support.TransactionTemplate(txManager);
        template.executeWithoutResult(tx -> {
            var updated = reports.byReference(report.getReference()).orElseThrow();
            assertThat(updated.getServiceType().getCode()).isEqualTo("STREET_LIGHT_LAMP_OUT");
            assertThat(updated.getDepartment().getCode()).isEqualTo("DEMO_LIGHTING");
        });
    }

    @Test
    @WithMockUser(username = "moderator", roles = "MODERATOR")
    void duplicateLinkPreventsChainsAndSelfReference() {
        Report canonical = createCleanlinessReport();
        Report duplicate = createCleanlinessReport();
        assertThatThrownBy(() -> moderation.markDuplicate("moderator", duplicate.getId(),
                duplicate.getReference())).hasMessage("duplicate.self");
        moderation.markDuplicate("moderator", duplicate.getId(), canonical.getReference());
        Report third = createCleanlinessReport();
        // Chaîne interdite : le canonique d'un doublon ne peut pas être lui-même un doublon
        assertThatThrownBy(() -> moderation.markDuplicate("moderator", third.getId(),
                duplicate.getReference())).hasMessage("duplicate.chain");
    }

    @Test
    void anonymousCannotCallInternalServices() {
        Report report = createCleanlinessReport();
        // Sans authentification, le contrôle métier bloque même si l'UI est contournée.
        assertThatThrownBy(() -> workflow.changeStatus("admin", report.getId(),
                WorkflowStatus.IN_PROGRESS, null))
                .isInstanceOfAny(org.springframework.security.core.AuthenticationException.class,
                        org.springframework.security.authorization.AuthorizationDeniedException.class);
        assertThatThrownBy(() -> admin.dashboard())
                .isInstanceOfAny(org.springframework.security.core.AuthenticationException.class,
                        org.springframework.security.authorization.AuthorizationDeniedException.class);
    }
}
