package tn.civiccare.administration.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.administration.AdminFacade;

/** Tableau de bord interne : volumes, statuts, dossiers par équipe, délais réels. */
@Route(value = "admin", layout = AdminLayout.class)
@RolesAllowed({"AGENT", "MODERATOR", "ADMIN"})
public class AdminDashboardView extends VerticalLayout {

    public AdminDashboardView(AdminFacade admin) {
        add(new H2(getTranslation("admin.nav.dashboard")));
        AdminFacade.DashboardStats stats = admin.dashboard();

        HorizontalLayout tiles = new HorizontalLayout();
        tiles.setWrap(true);
        tiles.add(tile(getTranslation("admin.dashboard.total"), String.valueOf(stats.total())),
                tile(getTranslation("admin.dashboard.open"), String.valueOf(stats.open())),
                tile(getTranslation("admin.dashboard.inProgress"), String.valueOf(stats.inProgress())),
                tile(getTranslation("admin.dashboard.pendingReview"), String.valueOf(stats.pendingReview())),
                tile(getTranslation("admin.dashboard.avgClose"),
                        stats.avgCloseDays() == null ? "—" : String.format("%.1f", stats.avgCloseDays())),
                tile(getTranslation("admin.dashboard.outboxBacklog"), String.valueOf(stats.outboxBacklog())));
        add(tiles);

        add(new H3(getTranslation("admin.dashboard.byDepartment")));
        VerticalLayout byDept = new VerticalLayout();
        byDept.setPadding(false);
        byDept.setSpacing(false);
        long max = stats.byDepartment().values().stream().mapToLong(Long::longValue).max().orElse(1);
        stats.byDepartment().forEach((name, count) -> {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            Span label = new Span(name + " : " + count);
            label.setWidth("340px");
            Div bar = new Div();
            bar.getStyle().set("background", "var(--lumo-primary-color)")
                    .set("height", "12px")
                    .set("border-radius", "6px")
                    .set("inline-size", Math.max(2, count * 100 / Math.max(1, max)) + "%");
            Div barTrack = new Div(bar);
            barTrack.setWidthFull();
            row.add(label, barTrack);
            row.expand(barTrack);
            byDept.add(row);
        });
        add(byDept);
    }

    private Div tile(String label, String value) {
        Div tile = new Div();
        tile.addClassName("report-card");
        tile.getStyle().set("min-width", "160px").set("text-align", "center");
        Span number = new Span(value);
        number.getStyle().set("font-size", "var(--lumo-font-size-xxl)").set("font-weight", "700")
                .set("display", "block").set("color", "var(--lumo-primary-text-color)");
        Span caption = new Span(label);
        caption.getStyle().set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        tile.add(number, caption);
        return tile;
    }
}
