package tn.civiccare.notifications;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Construction des e-mails dans la langue du destinataire (français par défaut, arabe).
 * Textes externalisés dans i18n/messages*.properties ; jamais d'e-mail dans les URLs.
 */
@Component
public class EmailComposer {

    private final String baseUrl;

    public EmailComposer(@Value("${civiccare.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    public String baseUrl() {
        return baseUrl;
    }

    public String subject(Locale locale, String key, Object... args) {
        return msg(locale, key, args);
    }

    public String reportCreatedBody(Locale locale, String reference, String subscribeUrl) {
        return msg(locale, "email.report.created.body", reference, baseUrl + "/requests/" + reference, subscribeUrl);
    }

    public String statusChangedBody(Locale locale, String reference, String statusLabel, String unsubscribeUrl) {
        return msg(locale, "email.report.status.body", reference, statusLabel,
                baseUrl + "/requests/" + reference, unsubscribeUrl);
    }

    public String subscriptionConfirmBody(Locale locale, String reference, String confirmUrl) {
        return msg(locale, "email.subscription.confirm.body", reference, confirmUrl);
    }

    public String contactCopyBody(Locale locale, String name, String message) {
        return msg(locale, "email.contact.copy.body", name, message);
    }

    private String msg(Locale locale, String key, Object... args) {
        Locale effective = tn.civiccare.shared.i18n.TranslationProvider.supported(locale);
        ResourceBundle bundle = tn.civiccare.shared.i18n.TranslationProvider.bundle(effective);
        String pattern = bundle.getString(key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, effective).format(args);
    }
}
