package tn.civiccare.shared.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.VaadinSession;
import tn.civiccare.shared.i18n.TranslationProvider;

import java.util.Locale;

/**
 * Layout public : en-tête sobre, navigation, sélecteur Français/العربية avec bascule RTL.
 * La direction du document suit la locale ; la carte géographique n'est jamais inversée.
 */
@com.vaadin.flow.server.auth.AnonymousAllowed
public class PublicLayout extends AppLayout implements LocaleChangeObserver {

    private final H1 title = new H1();
    private final RouterLink exploreLink = new RouterLink();
    private final RouterLink reportLink = new RouterLink();
    private final RouterLink followingLink = new RouterLink();
    private final RouterLink infoLink = new RouterLink();
    private final RouterLink contactLink = new RouterLink();
    private final Anchor loginLink = new Anchor("/admin", "");
    private final Select<Locale> language = new Select<>();

    private final String appName;

    public PublicLayout(@org.springframework.beans.factory.annotation.Value(
            "${civiccare.app-name:CivicCare Tunis}") String appName) {
        this.appName = appName;
        setPrimarySection(Section.NAVBAR);

        title.setText(appName);
        title.addClassName("app-title");

        exploreLink.setRoute(tn.civiccare.reports.ui.ExploreView.class);
        reportLink.setRoute(tn.civiccare.reports.ui.ReportWizardView.class);
        followingLink.setRoute(tn.civiccare.subscriptions.ui.FollowingView.class);
        infoLink.setRoute(tn.civiccare.content.ui.InfoView.class);
        contactLink.setRoute(tn.civiccare.content.ui.ContactView.class);

        language.setItems(TranslationProvider.FRENCH, TranslationProvider.ARABIC, TranslationProvider.ENGLISH);
        language.setItemLabelGenerator(l -> switch (l == null ? "fr" : l.getLanguage()) {
            case "ar" -> getTranslation("app.language.ar");
            case "en" -> getTranslation("app.language.en");
            default -> getTranslation("app.language.fr");
        });
        language.setValue(currentLocale());
        language.setWidth("130px");
        language.getElement().setAttribute("aria-label", getTranslation("app.language"));
        language.addValueChangeListener(e -> {
            if (e.getValue() != null && e.isFromClient() && !e.getValue().equals(e.getOldValue())) {
                VaadinSession.getCurrent().setLocale(e.getValue());
                // Rechargement : reconstruit toutes les vues dans la nouvelle locale (LTR/RTL inclus)
                UI.getCurrent().getPage().reload();
            }
        });

        Button reportCta = new Button(getTranslation("home.report.cta"), VaadinIcon.MEGAPHONE.create());
        reportCta.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        reportCta.addClickListener(e -> UI.getCurrent().navigate("report"));
        reportCta.setId("cta-report");

        HorizontalLayout nav = new HorizontalLayout(exploreLink, followingLink, infoLink, contactLink, loginLink);
        nav.setSpacing(true);
        nav.addClassName("app-nav");

        HorizontalLayout header = new HorizontalLayout(title, nav, language, reportCta);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.expand(nav);
        header.addClassName("app-header");
        header.setWrap(true);

        addToNavbar(header);
        applyTexts();
        applyDirection(currentLocale());
    }

    private Locale currentLocale() {
        Locale l = UI.getCurrent() != null ? UI.getCurrent().getLocale() : TranslationProvider.FRENCH;
        return TranslationProvider.supported(l);
    }

    private void applyTexts() {
        exploreLink.setText(getTranslation("nav.explore"));
        reportLink.setText(getTranslation("nav.report"));
        followingLink.setText(getTranslation("nav.following"));
        infoLink.setText(getTranslation("nav.info"));
        contactLink.setText(getTranslation("nav.contact"));
        loginLink.setText(getTranslation("nav.login"));
        title.setText(appName);
    }

    private void applyDirection(Locale locale) {
        UI ui = UI.getCurrent();
        if (ui != null) {
            ui.setDirection(TranslationProvider.isRtl(locale)
                    ? com.vaadin.flow.component.Direction.RIGHT_TO_LEFT
                    : com.vaadin.flow.component.Direction.LEFT_TO_RIGHT);
        }
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        applyTexts();
        applyDirection(event.getLocale());
        language.setValue(TranslationProvider.supported(event.getLocale()));
    }

    /** Bandeau de démonstration + avertissement urgences, à insérer par les vues. */
    public static Span demoBanner() {
        Span banner = new Span();
        banner.addClassName("emergency-banner");
        return banner;
    }
}
