package tn.civiccare.administration.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.administration.AdminFacade;
import tn.civiccare.administration.AdminFacade.WorkQueueFilter;
import tn.civiccare.administration.AdminFacade.WorkQueueRow;
import tn.civiccare.moderation.ModerationService;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.UiFormats;

/** File de modération : publication manuelle avant diffusion (politique initiale). */
@Route(value = "admin/moderation", layout = AdminLayout.class)
@RolesAllowed({"MODERATOR", "ADMIN"})
public class AdminModerationView extends VerticalLayout {

    private final Grid<WorkQueueRow> grid = new Grid<>();
    private final AdminFacade admin;
    private final String username;

    public AdminModerationView(AdminFacade admin, ModerationService moderation,
                               AppProperties props, AuthenticationContext authContext) {
        this.admin = admin;
        this.username = authContext.getPrincipalName().orElseThrow();
        setSizeFull();
        add(new H2(getTranslation("admin.moderation.title")));

        grid.addColumn(WorkQueueRow::reference).setHeader(getTranslation("admin.reports.reference"))
                .setWidth("130px").setFlexGrow(0);
        grid.addColumn(WorkQueueRow::typeLabelFr).setHeader(getTranslation("admin.reports.type")).setFlexGrow(2);
        grid.addColumn(row -> UiFormats.dateTime(row.createdAt(), props.zoneId(), getLocale()))
                .setHeader(getTranslation("admin.reports.created"));
        grid.addComponentColumn(row -> {
            Button open = new Button(getTranslation("wizard.duplicates.view"),
                    e -> UI.getCurrent().navigate("admin/report/" + row.id()));
            open.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            Button publish = new Button(getTranslation("admin.moderation.publish"), e -> {
                moderation.setPublication(username, row.id(), PublicationStatus.PUBLISHED, null);
                refresh();
            });
            publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            Button hide = new Button(getTranslation("admin.moderation.hide"), e -> {
                moderation.setPublication(username, row.id(), PublicationStatus.HIDDEN, null);
                refresh();
            });
            hide.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            return new HorizontalLayout(open, publish, hide);
        }).setHeader(getTranslation("common.actions")).setAutoWidth(true);
        grid.setSizeFull();
        add(grid);
        expand(grid);
        refresh();
    }

    private void refresh() {
        var rows = admin.workQueue(username,
                new WorkQueueFilter(null, PublicationStatus.PENDING_REVIEW, null, false, false), 0, 100);
        grid.setItems(rows);
        if (rows.isEmpty()) {
            add(new Span(getTranslation("admin.moderation.empty")));
        }
    }
}
