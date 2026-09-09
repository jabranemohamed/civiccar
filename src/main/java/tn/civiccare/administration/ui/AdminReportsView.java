package tn.civiccare.administration.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.administration.AdminFacade;
import tn.civiccare.administration.AdminFacade.WorkQueueFilter;
import tn.civiccare.administration.AdminFacade.WorkQueueRow;
import tn.civiccare.identity.Department;
import tn.civiccare.identity.StaffUserService;
import tn.civiccare.reports.PublicationStatus;
import tn.civiccare.reports.WorkflowStatus;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.UiFormats;

import java.nio.charset.StandardCharsets;

/** File de travail : Grid paginée côté serveur, filtres, export CSV. */
@Route(value = "admin/reports", layout = AdminLayout.class)
@RolesAllowed({"AGENT", "MODERATOR", "ADMIN"})
public class AdminReportsView extends VerticalLayout {

    private final Grid<WorkQueueRow> grid = new Grid<>();
    private final Select<WorkflowStatus> status = new Select<>();
    private final Select<PublicationStatus> publication = new Select<>();
    private final Select<Department> department = new Select<>();
    private final Checkbox onlyMine = new Checkbox();
    private final Checkbox onlyUnassigned = new Checkbox();

    public AdminReportsView(AdminFacade admin, StaffUserService userService, AppProperties props,
                            com.vaadin.flow.spring.security.AuthenticationContext authContext) {
        setSizeFull();
        add(new H2(getTranslation("admin.reports.title")));
        String username = authContext.getPrincipalName().orElseThrow();

        status.setLabel(getTranslation("admin.reports.status"));
        status.setItems(WorkflowStatus.values());
        status.setItemLabelGenerator(s -> s == null
                ? getTranslation("filter.all") : getTranslation("status." + s.name()));
        status.setEmptySelectionAllowed(true);
        status.setEmptySelectionCaption(getTranslation("filter.all"));

        publication.setLabel(getTranslation("admin.reports.publication"));
        publication.setItems(PublicationStatus.values());
        publication.setItemLabelGenerator(p -> p == null
                ? getTranslation("filter.all") : getTranslation("publication." + p.name()));
        publication.setEmptySelectionAllowed(true);
        publication.setEmptySelectionCaption(getTranslation("filter.all"));

        department.setLabel(getTranslation("admin.reports.department"));
        department.setItems(userService.allDepartments());
        department.setItemLabelGenerator(d -> d == null
                ? getTranslation("filter.all") : d.getNameFr());
        department.setEmptySelectionAllowed(true);
        department.setEmptySelectionCaption(getTranslation("filter.all"));

        onlyMine.setLabel(getTranslation("admin.reports.filter.mine"));
        onlyUnassigned.setLabel(getTranslation("admin.reports.unassigned"));

        status.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        publication.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        department.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        onlyMine.addValueChangeListener(e -> grid.getDataProvider().refreshAll());
        onlyUnassigned.addValueChangeListener(e -> grid.getDataProvider().refreshAll());

        Anchor export = new Anchor(DownloadHandler.fromInputStream(request ->
                new com.vaadin.flow.server.streams.DownloadResponse(
                        new java.io.ByteArrayInputStream(
                                admin.exportCsv(username, currentFilter()).getBytes(StandardCharsets.UTF_8)),
                        "dossiers.csv", "text/csv", -1)),
                "");
        Button exportButton = new Button(getTranslation("admin.dashboard.export"));
        exportButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        export.add(exportButton);

        HorizontalLayout filters = new HorizontalLayout(status, publication, department,
                onlyMine, onlyUnassigned, export);
        filters.setAlignItems(Alignment.END);
        filters.setWrap(true);
        add(filters);

        grid.addColumn(WorkQueueRow::reference)
                .setHeader(getTranslation("admin.reports.reference")).setWidth("130px").setFlexGrow(0);
        grid.addColumn(WorkQueueRow::typeLabelFr)
                .setHeader(getTranslation("admin.reports.type")).setFlexGrow(2);
        grid.addColumn(row -> getTranslation("status." + row.status().name()))
                .setHeader(getTranslation("admin.reports.status"));
        grid.addColumn(row -> getTranslation("publication." + row.publication().name()))
                .setHeader(getTranslation("admin.reports.publication"));
        grid.addColumn(WorkQueueRow::departmentName)
                .setHeader(getTranslation("admin.reports.department"));
        grid.addColumn(row -> row.assigneeName() == null
                        ? getTranslation("admin.reports.unassigned") : row.assigneeName())
                .setHeader(getTranslation("admin.reports.assignee"));
        grid.addColumn(row -> UiFormats.dateTime(row.createdAt(), props.zoneId(), getLocale()))
                .setHeader(getTranslation("admin.reports.created"));
        grid.setSizeFull();
        grid.addItemClickListener(e ->
                UI.getCurrent().navigate("admin/report/" + e.getItem().id()));

        CallbackDataProvider<WorkQueueRow, Void> provider = DataProvider.fromCallbacks(
                query -> admin.workQueue(username, currentFilter(), query.getOffset(), query.getLimit()).stream(),
                query -> (int) admin.workQueueCount(username, currentFilter()));
        grid.setItems(provider);
        add(grid);
        expand(grid);
    }

    private WorkQueueFilter currentFilter() {
        return new WorkQueueFilter(status.getValue(), publication.getValue(),
                department.getValue() == null ? null : department.getValue().getId(),
                Boolean.TRUE.equals(onlyMine.getValue()),
                Boolean.TRUE.equals(onlyUnassigned.getValue()));
    }
}
