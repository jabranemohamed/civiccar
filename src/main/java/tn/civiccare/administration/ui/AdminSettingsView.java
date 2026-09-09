package tn.civiccare.administration.ui;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import tn.civiccare.administration.AuditService;
import tn.civiccare.geo.BoundaryService;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.UiFormats;

/** Paramètres effectifs (lecture) + journal d'audit récent. */
@Route(value = "admin/settings", layout = AdminLayout.class)
@RolesAllowed("ADMIN")
public class AdminSettingsView extends VerticalLayout {

    public AdminSettingsView(AppProperties props, BoundaryService boundary, AuditService audit) {
        add(new H2(getTranslation("admin.settings.title")));

        add(new H3(getTranslation("admin.settings.boundary")));
        BoundaryService.BoundaryInfo info = boundary.activeBoundaryInfo();
        if (info.demo()) {
            Span warning = new Span(getTranslation("admin.settings.boundary.demo"));
            warning.addClassName("emergency-banner");
            add(warning);
        }
        add(row("Code", info.code()),
                row("Nom", info.nameFr()),
                row("Source", info.source()),
                row("Licence", info.license()),
                row("Fuseau", props.timezone()),
                row("Rayon doublons (m)", String.valueOf(props.duplicateRadiusMeters())),
                row("Archivage (jours après clôture)", String.valueOf(props.archiveAfterDays())),
                row("Purge données personnelles (jours)", String.valueOf(props.purgeAfterDays())),
                row("Géocodeur", props.geo().geocoderMode()),
                row("Tuiles", props.map().tileUrl()),
                row("Juridiction Open311 (démo)", props.open311().jurisdictionId()));

        add(new H3(getTranslation("admin.audit.title")));
        VerticalLayout auditList = new VerticalLayout();
        auditList.setPadding(false);
        auditList.setSpacing(false);
        audit.latest(50).forEach(event -> {
            Span line = new Span(UiFormats.dateTime(event.getCreatedAt(), props.zoneId(), getLocale())
                    + " · " + event.getActor() + " · " + event.getAction()
                    + " · " + event.getTargetType() + "/" + event.getTargetId());
            line.getStyle().set("font-size", "var(--lumo-font-size-s)");
            auditList.add(line);
        });
        add(auditList);
    }

    private Span row(String label, String value) {
        Span span = new Span(label + " : " + (value == null ? "—" : value));
        span.getStyle().set("display", "block");
        return span;
    }
}
