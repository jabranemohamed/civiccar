package tn.civiccare.reports.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.notifications.EmailOutboxService;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.reports.PublicReportFacade;
import tn.civiccare.reports.PublicReportFacade.PublicDetail;
import tn.civiccare.shared.AppProperties;
import tn.civiccare.shared.RateLimiter;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.shared.ui.UiFormats;
import tn.civiccare.subscriptions.SubscriptionService;
import tn.civiccare.subscriptions.ui.DeviceCookie;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fiche publique d'un signalement. Un dossier non publié répond exactement comme un
 * dossier inexistant, sans divulguer son contenu.
 */
@Route(value = "requests", layout = PublicLayout.class)
@AnonymousAllowed
public class RequestDetailView extends VerticalLayout implements HasUrlParameter<String>, HasDynamicTitle {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final PublicReportFacade facade;
    private final SubscriptionService subscriptions;
    private final EmailOutboxService emailOutbox;
    private final EmailComposer emails;
    private final AppProperties props;
    private final RateLimiter rateLimiter;
    private final org.springframework.transaction.PlatformTransactionManager txManager;

    private String title = "CivicCare Tunis";

    public RequestDetailView(PublicReportFacade facade, SubscriptionService subscriptions,
                             EmailOutboxService emailOutbox, EmailComposer emails,
                             AppProperties props, RateLimiter rateLimiter,
                             org.springframework.transaction.PlatformTransactionManager txManager) {
        this.facade = facade;
        this.subscriptions = subscriptions;
        this.emailOutbox = emailOutbox;
        this.emails = emails;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.txManager = txManager;
        setMaxWidth("860px");
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void setParameter(BeforeEvent event, String reference) {
        removeAll();
        Locale locale = UI.getCurrent().getLocale();
        facade.byReference(reference, locale).ifPresentOrElse(
                detail -> render(detail, locale),
                () -> {
                    title = getTranslation("detail.notFound");
                    H2 notFound = new H2(getTranslation("detail.notFound"));
                    Anchor back = new Anchor("/", getTranslation("nav.explore"));
                    add(notFound, back);
                });
    }

    private void render(PublicDetail detail, Locale locale) {
        boolean ar = "ar".equals(locale.getLanguage());
        title = getTranslation("detail.title", detail.reference());

        H2 heading = new H2(detail.typeLabel());
        Span group = new Span(detail.groupLabel() + " · " + detail.reference());
        group.getStyle().set("color", "var(--lumo-secondary-text-color)");

        HorizontalLayout statusRow = new HorizontalLayout(
                (com.vaadin.flow.component.Component)
                        UiFormats.statusBadge(detail.status(), detail.archived(), this),
                new Span(UiFormats.dateTime(detail.createdAt(), props.zoneId(), locale)));
        statusRow.setAlignItems(Alignment.CENTER);

        // ===== Suivre (favoris de cet appareil) =====
        Button followButton = new Button();
        followButton.setId("follow-button");
        var binding = DeviceCookie.bind(subscriptions);
        boolean followed = subscriptions.isBookmarked(binding.browserId(), detail.id());
        updateFollowText(followButton, followed);
        followButton.addClickListener(e -> {
            boolean nowFollowed = subscriptions.toggleBookmark(binding.browserId(), detail.id());
            updateFollowText(followButton, nowFollowed);
            if (nowFollowed) {
                Notification.show(getTranslation("detail.followed"));
            }
        });
        statusRow.add(followButton);

        add(heading, group, statusRow);

        // ===== Photos approuvées =====
        if (!detail.mediaKeys().isEmpty()) {
            H3 photosTitle = new H3(getTranslation("detail.photos"));
            HorizontalLayout gallery = new HorizontalLayout();
            gallery.setWrap(true);
            for (String key : detail.mediaKeys()) {
                Anchor link = new Anchor("/media/" + key, "");
                link.setTarget("_blank");
                Image img = new Image("/media/" + key, getTranslation("detail.photos"));
                img.setMaxWidth("260px");
                img.getStyle().set("border-radius", "var(--lumo-border-radius-m)");
                link.add(img);
                gallery.add(link);
            }
            add(photosTitle, gallery);
        }

        // ===== Localisation =====
        H3 addressTitle = new H3(getTranslation("detail.address"));
        VerticalLayout addressBlock = new VerticalLayout();
        addressBlock.setPadding(false);
        addressBlock.setSpacing(false);
        if (detail.address() != null) {
            addressBlock.add(new Span(detail.address()));
        }
        if (detail.addressDetails() != null) {
            addressBlock.add(new Span(detail.addressDetails()));
        }
        Span coords = new Span(String.format(Locale.ROOT, "%.5f, %.5f",
                detail.latitude(), detail.longitude()));
        coords.getStyle().set("color", "var(--lumo-tertiary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        coords.getElement().setAttribute("dir", "ltr");
        addressBlock.add(coords);
        add(addressTitle, addressBlock);

        // ===== Description publique et champs publics =====
        if (detail.description() != null && !detail.description().isBlank()) {
            H3 descTitle = new H3(getTranslation("detail.description"));
            Paragraph desc = new Paragraph(detail.description());
            desc.getStyle().set("white-space", "pre-wrap");
            add(descTitle, desc);
        }
        detail.publicFieldLabels().forEach((label, value) -> {
            Span field = new Span(label + " : " + value);
            add(field);
        });

        // ===== Frise chronologique =====
        H3 timelineTitle = new H3(getTranslation("detail.timeline"));
        VerticalLayout timeline = new VerticalLayout();
        timeline.setPadding(false);
        timeline.setSpacing(false);
        for (PublicReportFacade.TimelineEntry entry : detail.timeline()) {
            Div item = new Div();
            item.addClassName("timeline-entry");
            Span when = new Span(UiFormats.dateTime(entry.at(), props.zoneId(), locale));
            when.addClassName("timeline-date");
            Div what = new Div();
            if (entry.toStatus() != null) {
                what.setText(getTranslation("status." + entry.toStatus().name()));
                what.getStyle().set("font-weight", "600");
            } else {
                what.setText(entry.publicMessage());
                what.getStyle().set("white-space", "pre-wrap");
            }
            item.add(when, what);
            timeline.add(item);
        }
        add(timelineTitle, timeline);

        // ===== Abonnement e-mail =====
        H3 subscribeTitle = new H3(getTranslation("detail.subscribe.title"));
        EmailField email = new EmailField(getTranslation("detail.subscribe.email"));
        email.setWidthFull();
        email.setMaxWidth("400px");
        Checkbox consent = new Checkbox(getTranslation("detail.subscribe.consent"));
        Button subscribe = new Button(getTranslation("detail.subscribe.submit"), VaadinIcon.ENVELOPE.create());
        subscribe.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        subscribe.setId("subscribe-button");
        subscribe.addClickListener(e -> {
            String value = email.getValue() == null ? "" : email.getValue().trim();
            if (!EMAIL.matcher(value).matches()) {
                email.setInvalid(true);
                email.setErrorMessage(getTranslation("wizard.contact.email.invalid"));
                return;
            }
            if (!Boolean.TRUE.equals(consent.getValue())) {
                Notification.show(getTranslation("wizard.consent.required"));
                return;
            }
            try {
                rateLimiter.check("subscribe:" + binding.browserId(), 5, 3600);
                new org.springframework.transaction.support.TransactionTemplate(txManager)
                        .executeWithoutResult(tx -> {
                            String confirmUrl = subscriptions.requestSubscription(detail.id(), value);
                            if (confirmUrl != null) {
                                emailOutbox.enqueueEmail("subscription.confirm", value,
                                        emails.subject(UI.getCurrent().getLocale(),
                                                "email.subscription.confirm.subject", detail.reference()),
                                        emails.subscriptionConfirmBody(UI.getCurrent().getLocale(),
                                                detail.reference(), confirmUrl),
                                        null);
                            }
                        });
                // Même message quel que soit l'état réel : pas de divulgation d'abonnement.
                Notification.show(getTranslation("detail.subscribe.sent"), 6000,
                        Notification.Position.MIDDLE);
                email.clear();
                consent.setValue(false);
            } catch (Telemetry.ValidationException ex) {
                Notification.show(getTranslation("common.error"));
            }
        });
        add(subscribeTitle, email, consent, subscribe);
    }

    private void updateFollowText(Button button, boolean followed) {
        button.setText(getTranslation(followed ? "detail.unfollow" : "detail.follow"));
        button.setIcon(followed ? VaadinIcon.STAR.create() : VaadinIcon.STAR_O.create());
    }

    @Override
    public String getPageTitle() {
        return title;
    }
}
