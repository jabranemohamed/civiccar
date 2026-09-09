package tn.civiccare.subscriptions.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.subscriptions.SubscriptionService;

/**
 * Liens d'action e-mail : le GET affiche une confirmation, l'action n'est déclenchée
 * que par le clic (requête serveur Vaadin), jamais par un scanner de messagerie.
 */
public final class SubscriptionActionViews {

    private SubscriptionActionViews() {
    }

    @Route(value = "s/confirm", layout = PublicLayout.class)
    @AnonymousAllowed
    public static class ConfirmView extends VerticalLayout implements HasUrlParameter<String> {

        public ConfirmView(SubscriptionService subscriptions) {
            this.subscriptions = subscriptions;
            setAlignItems(Alignment.CENTER);
        }

        private final SubscriptionService subscriptions;
        private String token;

        @Override
        public void setParameter(BeforeEvent event, String token) {
            this.token = token;
            removeAll();
            add(new H2(getTranslation("subscription.confirm.title")),
                    new Paragraph(getTranslation("subscription.confirm.body")));
            Button confirm = new Button(getTranslation("subscription.confirm.button"));
            confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            confirm.setId("confirm-subscription");
            confirm.addClickListener(e -> {
                boolean ok = subscriptions.confirm(this.token);
                removeAll();
                add(new H2(getTranslation(ok ? "subscription.confirm.success"
                        : "subscription.confirm.invalid")));
            });
            add(confirm);
        }
    }

    @Route(value = "s/unsubscribe", layout = PublicLayout.class)
    @AnonymousAllowed
    public static class UnsubscribeView extends VerticalLayout implements HasUrlParameter<String> {

        private final SubscriptionService subscriptions;
        private String token;

        public UnsubscribeView(SubscriptionService subscriptions) {
            this.subscriptions = subscriptions;
            setAlignItems(Alignment.CENTER);
        }

        @Override
        public void setParameter(BeforeEvent event, String token) {
            this.token = token;
            removeAll();
            add(new H2(getTranslation("subscription.unsubscribe.title")),
                    new Paragraph(getTranslation("subscription.unsubscribe.body")));
            Button unsubscribe = new Button(getTranslation("subscription.unsubscribe.button"));
            unsubscribe.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            unsubscribe.setId("confirm-unsubscribe");
            unsubscribe.addClickListener(e -> {
                boolean ok = subscriptions.unsubscribe(this.token);
                removeAll();
                add(new H2(getTranslation(ok ? "subscription.unsubscribe.success"
                        : "subscription.confirm.invalid")));
            });
            add(unsubscribe);
        }
    }
}
