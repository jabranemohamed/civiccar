package tn.civiccare.content.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import tn.civiccare.content.ContentService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.ui.PublicLayout;
import tn.civiccare.subscriptions.SubscriptionService;
import tn.civiccare.subscriptions.ui.DeviceCookie;

/** Formulaire de contact général : validation serveur, honeypot discret, file d'envoi outbox. */
@Route(value = "contact", layout = PublicLayout.class)
@AnonymousAllowed
public class ContactView extends VerticalLayout {

    public ContactView(ContentService content, SubscriptionService subscriptions) {
        setMaxWidth("640px");
        getStyle().set("margin", "0 auto");

        add(new H2(getTranslation("contact.title")),
                new Paragraph(getTranslation("contact.intro")));

        TextField name = new TextField(getTranslation("contact.name"));
        name.setRequiredIndicatorVisible(true);
        name.setWidthFull();
        name.setId("contact-name");

        EmailField email = new EmailField(getTranslation("contact.email"));
        email.setRequiredIndicatorVisible(true);
        email.setWidthFull();
        email.setId("contact-email-field");

        TextArea message = new TextArea(getTranslation("contact.message"));
        message.setRequiredIndicatorVisible(true);
        message.setWidthFull();
        message.setMinHeight("140px");
        message.setMaxLength(4000);
        message.setId("contact-message");

        // Honeypot : champ invisible pour les humains, rempli par les robots.
        TextField website = new TextField("Website");
        website.getStyle().set("position", "absolute").set("left", "-9999px");
        website.setTabIndex(-1);
        website.getElement().setAttribute("aria-hidden", "true");
        website.getElement().setAttribute("autocomplete", "off");

        Checkbox copy = new Checkbox(getTranslation("contact.copy"));
        copy.setId("contact-copy");
        Checkbox consent = new Checkbox(getTranslation("contact.consent"));
        consent.setId("contact-consent");

        Button submit = new Button(getTranslation("contact.submit"));
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setId("contact-submit");
        submit.addClickListener(e -> {
            var binding = DeviceCookie.bind(subscriptions);
            try {
                content.submitContact(name.getValue(), email.getValue(), message.getValue(),
                        Boolean.TRUE.equals(copy.getValue()), Boolean.TRUE.equals(consent.getValue()),
                        website.getValue(), binding.browserId().toString(),
                        UI.getCurrent().getLocale());
                removeAll();
                add(new H2(getTranslation("contact.success")));
            } catch (ValidationException ex) {
                Notification.show(getTranslation("contact.error"), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        add(name, email, message, website, copy, consent, submit);
    }
}
