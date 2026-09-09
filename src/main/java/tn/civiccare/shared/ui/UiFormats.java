package tn.civiccare.shared.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import tn.civiccare.reports.WorkflowStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/** Formats d'affichage : dates dans le fuseau configuré (Africa/Tunis), badges de statut. */
public final class UiFormats {

    private UiFormats() {
    }

    /** Date/heure locale (Africa/Tunis par défaut), stockage UTC conservé. */
    public static String dateTime(Instant instant, ZoneId zone, Locale locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(zone)
                .format(instant);
    }

    public static String date(Instant instant, ZoneId zone, Locale locale) {
        if (instant == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(zone)
                .format(instant);
    }

    /** Badge de statut : icône + libellé textuel, jamais la couleur seule. */
    public static Component statusBadge(WorkflowStatus status, boolean archived, Component owner) {
        String key = archived ? "status.archived" : "status." + status.name();
        Span badge = new Span();
        Icon icon = switch (status) {
            case OPEN -> VaadinIcon.CIRCLE_THIN.create();
            case IN_PROGRESS -> VaadinIcon.COG.create();
            case DONE_OR_ORDERED -> VaadinIcon.CHECK_CIRCLE.create();
            case OUT_OF_SCOPE -> VaadinIcon.EXTERNAL_LINK.create();
        };
        icon.setSize("14px");
        Span label = new Span(owner.getTranslation(key));
        badge.add(icon, label);
        badge.addClassNames("status-badge", "status-" + status.name().toLowerCase(Locale.ROOT));
        return badge;
    }
}
