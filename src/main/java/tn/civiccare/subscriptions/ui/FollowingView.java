package tn.civiccare.subscriptions.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.reports.ReportQueryService;
import tn.civiccare.reports.ReportQueryService.PublicSummary;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.shared.ui.UiFormats;
import tn.civiccare.subscriptions.SubscriptionService;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Suivis de cet appareil (cookie navigateur), sans compte. Seuls les dossiers encore
 * publiés sont affichés : le jeton d'appareil ne donne aucun accès aux dossiers privés.
 */
@Route(value = "following", layout = PublicLayout.class)
@AnonymousAllowed
public class FollowingView extends VerticalLayout {

    public FollowingView(SubscriptionService subscriptions, ReportQueryService queries,
                         AppProperties props) {
        setMaxWidth("860px");
        getStyle().set("margin", "0 auto");

        var binding = DeviceCookie.bind(subscriptions);
        List<UUID> ids = subscriptions.bookmarkedReportIds(binding.browserId());
        List<PublicSummary> summaries = queries.publicSummariesByIds(ids);

        add(new H2(getTranslation("following.title") + " (" + summaries.size() + ")"));
        Span hint = new Span(getTranslation("following.hint"));
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(hint);

        if (summaries.isEmpty()) {
            add(new Span(getTranslation("following.empty")));
            return;
        }
        Locale locale = UI.getCurrent().getLocale();
        for (PublicSummary summary : summaries) {
            Div card = new Div();
            card.addClassName("report-card");
            Span type = new Span(summary.typeLabel(locale));
            type.getStyle().set("font-weight", "600");
            Span meta = new Span(summary.reference() + " · "
                    + UiFormats.date(summary.createdAt(), props.zoneId(), locale));
            meta.getStyle().set("color", "var(--lumo-tertiary-text-color)")
                    .set("font-size", "var(--lumo-font-size-xs)");
            HorizontalLayout content = new HorizontalLayout();
            content.setAlignItems(Alignment.CENTER);
            if (summary.thumbKey() != null) {
                Image thumb = new Image("/media/" + summary.thumbKey(), "");
                thumb.setWidth("64px");
                thumb.setHeight("64px");
                thumb.getStyle().set("object-fit", "cover")
                        .set("border-radius", "var(--lumo-border-radius-s)");
                content.add(thumb);
            }
            VerticalLayout text = new VerticalLayout(type,
                    (com.vaadin.flow.component.Component)
                            UiFormats.statusBadge(summary.status(), summary.archived(), this), meta);
            text.setPadding(false);
            text.setSpacing(false);
            content.add(text);
            card.add(content);
            card.addClickListener(e -> UI.getCurrent().navigate("requests/" + summary.reference()));
            add(card);
        }
    }
}
