package tn.civiccare.administration.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.administration.AdminFacade;
import tn.civiccare.catalog.CatalogService;
import tn.civiccare.catalog.ServiceType;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUser;
import tn.civiccare.identity.StaffUserService;
import tn.civiccare.media.MediaAsset;
import tn.civiccare.media.MediaService;
import tn.civiccare.moderation.ModerationService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.reports.*;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.UiFormats;

import java.util.List;
import java.util.UUID;

/**
 * Dossier interne : contact privé, textes source/public, médias, affectation,
 * transitions, modération, notes internes et messages publics nettement séparés.
 * L'accès objet est contrôlé au service ET revérifié ici.
 */
@Route(value = "admin/report", layout = AdminLayout.class)
@RolesAllowed({"AGENT", "MODERATOR", "ADMIN"})
public class AdminReportDetailView extends VerticalLayout implements HasUrlParameter<String> {

    private final ReportAdminQueries queries;
    private final AdminFacade admin;
    private final WorkflowService workflow;
    private final ModerationService moderation;
    private final MediaService media;
    private final CatalogService catalog;
    private final StaffUserService users;
    private final AppProperties props;
    private final AuthenticationContext authContext;

    private String username;
    private boolean isModerator;
    private UUID reportId;

    public AdminReportDetailView(ReportAdminQueries queries, AdminFacade admin, WorkflowService workflow,
                                 ModerationService moderation, MediaService media, CatalogService catalog,
                                 StaffUserService users, AppProperties props,
                                 AuthenticationContext authContext) {
        this.queries = queries;
        this.admin = admin;
        this.workflow = workflow;
        this.moderation = moderation;
        this.media = media;
        this.catalog = catalog;
        this.users = users;
        this.props = props;
        this.authContext = authContext;
        setMaxWidth("1000px");
    }

    @Override
    public void setParameter(BeforeEvent event, String id) {
        removeAll();
        username = authContext.getPrincipalName().orElseThrow();
        isModerator = authContext.hasAnyRole("MODERATOR", "ADMIN");
        try {
            reportId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            add(new H2(getTranslation("detail.notFound")));
            return;
        }
        Report report = queries.byId(reportId).orElse(null);
        if (report == null || !admin.canAccess(username,
                report.getDepartment() == null ? null : report.getDepartment().getId())) {
            add(new H2(getTranslation("detail.notFound")));
            return;
        }
        render(report);
    }

    private void render(Report report) {
        var locale = getLocale();
        H2 heading = new H2(report.getReference() + " — " + report.getServiceType().label(locale));
        HorizontalLayout badges = new HorizontalLayout(
                (com.vaadin.flow.component.Component) UiFormats.statusBadge(
                        report.getWorkflowStatus(), report.getArchivedAt() != null, this),
                new Span(getTranslation("publication." + report.getPublicationStatus().name())),
                new Span(UiFormats.dateTime(report.getCreatedAt(), props.zoneId(), locale)));
        badges.setAlignItems(Alignment.CENTER);
        add(heading, badges);

        // ===== Localisation =====
        Span position = new Span(getTranslation("detail.address") + " : "
                + (report.getAddress() == null ? "" : report.getAddress() + " · ")
                + String.format(java.util.Locale.ROOT, "%.5f, %.5f",
                report.getLocation().getY(), report.getLocation().getX()));
        add(position);

        // ===== Contact privé =====
        Div contactCard = new Div();
        contactCard.addClassName("report-card");
        contactCard.add(new H4(getTranslation("admin.report.contact")));
        queries.contact(report.getId()).ifPresent(contact -> {
            if (contact.isPurged()) {
                contactCard.add(new Span(getTranslation("admin.report.contact.purged")));
            } else {
                contactCard.add(new Div(new Span("E-mail : " + orDash(contact.getEmail()))));
                contactCard.add(new Div(new Span("Téléphone : " + orDash(contact.getPhone()))));
                if (contact.getVehiclePlate() != null) {
                    contactCard.add(new Div(new Span(getTranslation("admin.report.plate")
                            + " : " + contact.getVehiclePlate())));
                }
            }
        });
        add(contactCard);

        // ===== Textes =====
        Div texts = new Div();
        texts.addClassName("report-card");
        texts.add(new H4(getTranslation("admin.report.privateDescription")));
        Paragraph privateText = new Paragraph(orDash(report.getDescriptionPrivate()));
        privateText.getStyle().set("white-space", "pre-wrap");
        texts.add(privateText);
        texts.add(new H4(getTranslation("admin.report.publicDescription")));
        if (isModerator) {
            TextArea publicText = new TextArea();
            publicText.setWidthFull();
            publicText.setValue(report.getDescriptionPublic() == null ? "" : report.getDescriptionPublic());
            publicText.setMaxLength(400);
            Button saveText = new Button(getTranslation("admin.moderation.editText"), e -> action(() -> {
                moderation.editPublicText(username, reportId, publicText.getValue());
                reload();
            }));
            saveText.addThemeVariants(ButtonVariant.LUMO_SMALL);
            texts.add(publicText, saveText);
        } else {
            Paragraph publicText = new Paragraph(orDash(report.getDescriptionPublic()));
            publicText.getStyle().set("white-space", "pre-wrap");
            texts.add(publicText);
        }
        report.getFieldValues().forEach((code, value) ->
                texts.add(new Div(new Span(code + " : " + value))));
        add(texts);

        // ===== Médias =====
        List<MediaAsset> assets = media.allFor(report.getId());
        if (!assets.isEmpty()) {
            Div mediaCard = new Div();
            mediaCard.addClassName("report-card");
            mediaCard.add(new H4(getTranslation("detail.photos")));
            HorizontalLayout gallery = new HorizontalLayout();
            gallery.setWrap(true);
            for (MediaAsset asset : assets) {
                VerticalLayout item = new VerticalLayout();
                item.setPadding(false);
                item.setSpacing(false);
                Image img = new Image("/media/" + asset.getThumbKey(), asset.getModerationStatus().name());
                img.setWidth("140px");
                item.add(img, new Span(asset.getModerationStatus().name()));
                if (isModerator) {
                    HorizontalLayout actions = new HorizontalLayout();
                    if (asset.getModerationStatus() != MediaAsset.ModerationStatus.APPROVED) {
                        Button approve = new Button(getTranslation("admin.moderation.approveMedia"),
                                e -> action(() -> {
                                    moderation.moderateMedia(username, reportId, asset.getId(), true, null);
                                    reload();
                                }));
                        approve.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_SUCCESS);
                        actions.add(approve);
                    }
                    if (asset.getModerationStatus() != MediaAsset.ModerationStatus.REMOVED) {
                        Button remove = new Button(getTranslation("admin.moderation.removeMedia"),
                                e -> action(() -> {
                                    moderation.moderateMedia(username, reportId, asset.getId(), false, null);
                                    reload();
                                }));
                        remove.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                        actions.add(remove);
                    }
                    item.add(actions);
                }
                gallery.add(item);
            }
            mediaCard.add(gallery);
            add(mediaCard);
        }

        // ===== Modération de publication =====
        if (isModerator) {
            Div moderationCard = new Div();
            moderationCard.addClassName("report-card");
            moderationCard.add(new H4(getTranslation("admin.moderation.title")));
            TextField reason = new TextField(getTranslation("admin.moderation.reason"));
            Button publish = new Button(getTranslation("admin.moderation.publish"), e -> action(() -> {
                moderation.setPublication(username, reportId, PublicationStatus.PUBLISHED, reason.getValue());
                reload();
            }));
            publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            publish.setId("publish-button");
            Button hide = new Button(getTranslation("admin.moderation.hide"), e -> action(() -> {
                moderation.setPublication(username, reportId, PublicationStatus.HIDDEN, reason.getValue());
                reload();
            }));
            hide.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            HorizontalLayout row = new HorizontalLayout(reason, publish, hide);
            row.setAlignItems(Alignment.END);
            moderationCard.add(row);

            // Correction de catégorie
            Select<ServiceType> newType = new Select<>();
            newType.setLabel(getTranslation("admin.report.changeCategory"));
            newType.setItems(catalog.allTypes());
            newType.setItemLabelGenerator(t -> t.getGroup().label(locale) + " — " + t.label(locale));
            newType.setWidth("380px");
            Button applyType = new Button(getTranslation("common.confirm"), e -> {
                if (newType.getValue() != null) {
                    action(() -> {
                        moderation.changeCategory(username, reportId, newType.getValue().getId());
                        reload();
                    });
                }
            });
            applyType.addThemeVariants(ButtonVariant.LUMO_SMALL);
            HorizontalLayout typeRow = new HorizontalLayout(newType, applyType);
            typeRow.setAlignItems(Alignment.END);
            moderationCard.add(typeRow);
            add(moderationCard);
        }

        // ===== Affectation =====
        Div assignCard = new Div();
        assignCard.addClassName("report-card");
        assignCard.add(new H4(getTranslation("admin.report.assign")));
        Select<Department> dept = new Select<>();
        dept.setLabel(getTranslation("admin.report.assignDepartment"));
        dept.setItems(users.allDepartments());
        dept.setItemLabelGenerator(Department::getNameFr);
        if (report.getDepartment() != null) {
            users.allDepartments().stream()
                    .filter(d -> d.getId().equals(report.getDepartment().getId()))
                    .findFirst().ifPresent(dept::setValue);
        }
        Select<StaffUser> agent = new Select<>();
        agent.setLabel(getTranslation("admin.report.assignAgent"));
        agent.setItemLabelGenerator(u -> u == null
                ? getTranslation("admin.reports.unassigned") : u.getDisplayName());
        Runnable refreshAgents = () -> {
            Department selected = dept.getValue();
            agent.setItems(selected == null ? List.of() : users.agentsOfDepartment(selected.getId()));
        };
        refreshAgents.run();
        // setEmptySelectionAllowed exige que les items soient déjà définis (NPE sinon)
        agent.setEmptySelectionAllowed(true);
        agent.setEmptySelectionCaption(getTranslation("admin.reports.unassigned"));
        if (report.getAssignee() != null) {
            users.allUsers().stream()
                    .filter(u -> u.getId().equals(report.getAssignee().getId()))
                    .findFirst().ifPresent(agent::setValue);
        }
        dept.addValueChangeListener(e -> refreshAgents.run());
        Button assign = new Button(getTranslation("admin.report.assign"), e -> action(() -> {
            workflow.assign(username, reportId,
                    dept.getValue() == null ? null : dept.getValue().getId(),
                    agent.getValue() == null ? null : agent.getValue().getId());
            reload();
        }));
        assign.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        assign.setId("assign-button");
        HorizontalLayout assignRow = new HorizontalLayout(dept, agent, assign);
        assignRow.setAlignItems(Alignment.END);
        assignRow.setWrap(true);
        assignCard.add(assignRow);
        add(assignCard);

        // ===== Changement de statut =====
        Div statusCard = new Div();
        statusCard.addClassName("report-card");
        statusCard.add(new H4(getTranslation("admin.report.changeStatus")));
        Select<WorkflowStatus> target = new Select<>();
        target.setItems(report.getWorkflowStatus().allowedTransitions().stream().toList());
        target.setItemLabelGenerator(s -> getTranslation("status." + s.name()));
        TextField statusReason = new TextField(getTranslation("admin.report.statusReason"));
        statusReason.setWidth("300px");
        Button apply = new Button(getTranslation("common.confirm"), e -> {
            if (target.getValue() == null) {
                return;
            }
            action(() -> {
                workflow.changeStatus(username, reportId, target.getValue(), statusReason.getValue());
                reload();
            });
        });
        apply.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        apply.setId("apply-status");
        HorizontalLayout statusRow = new HorizontalLayout(target, statusReason, apply);
        statusRow.setAlignItems(Alignment.END);
        statusRow.setWrap(true);
        statusCard.add(statusRow);

        // Doublon confirmé
        TextField canonicalRef = new TextField(getTranslation("admin.report.duplicateOf"));
        Button markDuplicate = new Button(getTranslation("admin.report.markDuplicate"), e -> action(() -> {
            moderation.markDuplicate(username, reportId, canonicalRef.getValue());
            reload();
        }));
        markDuplicate.addThemeVariants(ButtonVariant.LUMO_SMALL);
        queries.duplicateLink(reportId).ifPresentOrElse(link ->
                        queries.plainById(link.getCanonicalId()).ifPresent(canonical ->
                                statusCard.add(new Div(new Span(getTranslation("admin.report.markDuplicate")
                                        + " → " + canonical.getReference())))),
                () -> {
                    HorizontalLayout dupRow = new HorizontalLayout(canonicalRef, markDuplicate);
                    dupRow.setAlignItems(Alignment.END);
                    statusCard.add(dupRow);
                });
        add(statusCard);

        // ===== Messages publics =====
        Div updatesCard = new Div();
        updatesCard.addClassName("report-card");
        updatesCard.add(new H4(getTranslation("admin.report.publicUpdates")));
        for (PublicUpdate update : queries.publicUpdates(reportId)) {
            Div entry = new Div();
            entry.addClassName("timeline-entry");
            Span when = new Span(UiFormats.dateTime(update.getCreatedAt(), props.zoneId(), locale));
            when.addClassName("timeline-date");
            entry.add(when, new Div(new Span(update.getBody())));
            updatesCard.add(entry);
        }
        TextArea newUpdate = new TextArea(getTranslation("admin.report.addUpdate"));
        newUpdate.setWidthFull();
        newUpdate.setMaxLength(1000);
        Button addUpdate = new Button(getTranslation("admin.report.addUpdate"), e -> {
            if (!newUpdate.getValue().isBlank()) {
                action(() -> {
                    workflow.addPublicUpdate(username, reportId, newUpdate.getValue());
                    reload();
                });
            }
        });
        addUpdate.addThemeVariants(ButtonVariant.LUMO_SMALL);
        updatesCard.add(newUpdate, addUpdate);
        add(updatesCard);

        // ===== Notes internes =====
        Div notesCard = new Div();
        notesCard.addClassName("report-card");
        notesCard.getStyle().set("background", "var(--lumo-contrast-5pct)");
        notesCard.add(new H4(getTranslation("admin.report.internalNotes")));
        for (InternalNote note : queries.internalNotes(reportId)) {
            Div entry = new Div();
            entry.addClassName("timeline-entry");
            Span when = new Span(UiFormats.dateTime(note.getCreatedAt(), props.zoneId(), locale));
            when.addClassName("timeline-date");
            entry.add(when, new Div(new Span(note.getBody())));
            notesCard.add(entry);
        }
        TextArea newNote = new TextArea(getTranslation("admin.report.addNote"));
        newNote.setWidthFull();
        newNote.setMaxLength(2000);
        Button addNote = new Button(getTranslation("admin.report.addNote"), e -> {
            if (!newNote.getValue().isBlank()) {
                action(() -> {
                    workflow.addInternalNote(username, reportId, newNote.getValue());
                    reload();
                });
            }
        });
        addNote.addThemeVariants(ButtonVariant.LUMO_SMALL);
        notesCard.add(newNote, addNote);
        add(notesCard);

        // ===== Historique des statuts =====
        Div historyCard = new Div();
        historyCard.addClassName("report-card");
        historyCard.add(new H4(getTranslation("detail.timeline")));
        for (StatusEvent event : queries.statusHistory(reportId)) {
            Div entry = new Div();
            entry.addClassName("timeline-entry");
            Span when = new Span(UiFormats.dateTime(event.getCreatedAt(), props.zoneId(), locale));
            when.addClassName("timeline-date");
            String text = (event.getFromStatus() == null ? ""
                    : getTranslation("status." + event.getFromStatus().name()) + " → ")
                    + getTranslation("status." + event.getToStatus().name())
                    + (event.getReason() == null ? "" : " (" + event.getReason() + ")");
            entry.add(when, new Div(new Span(text)));
            historyCard.add(entry);
        }
        add(historyCard);
    }

    private void action(Runnable runnable) {
        try {
            runnable.run();
        } catch (ValidationException e) {
            String key = "reopen.reasonRequired".equals(e.getMessage())
                    ? "admin.report.reopenReasonRequired" : "common.error";
            Notification.show(getTranslation(key), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            Notification.show(getTranslation("common.error"), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } catch (org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            // Verrouillage optimiste : un autre agent a modifié le dossier.
            Notification.show(getTranslation("common.error") + " (conflit d'édition)", 5000,
                    Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            reload();
        }
    }

    private void reload() {
        UI.getCurrent().getPage().reload();
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
