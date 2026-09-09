package tn.civiccare.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import tn.civiccare.AbstractIntegrationTest;
import tn.civiccare.notifications.OutboxDispatcher;
import tn.civiccare.observability.Telemetry.ValidationException;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A15 : formulaire de contact enregistré + copie facultative + anti-spam + consentement. */
class ContactIT extends AbstractIntegrationTest {

    @Autowired
    ContentService content;
    @Autowired
    OutboxDispatcher dispatcher;
    @Autowired
    RecordingMailSender mailSender;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void contactMessageIsStoredAndCopyDeliveredWhenRequested() {
        mailSender.sent.clear();
        String client = UUID.randomUUID().toString();
        content.submitContact("Amina Test", "amina@example.com", "Bonjour, question sur le service.",
                true, true, "", client, Locale.forLanguageTag("fr"));

        assertThat(content.inbox())
                .anyMatch(m -> m.getEmail().equals("amina@example.com")
                        && m.getStatus() == ContactMessage.Status.NEW);

        dispatcher.dispatchDue();
        assertThat(mailSender.sent)
                .anyMatch(m -> m.getTo()[0].equals("amina@example.com")
                        && m.getText().contains("question sur le service"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void copyNotSentWhenNotRequested() {
        mailSender.sent.clear();
        content.submitContact("Sans Copie", "sanscopie@example.com", "Pas de copie svp.",
                false, true, "", UUID.randomUUID().toString(), Locale.forLanguageTag("fr"));
        dispatcher.dispatchDue();
        assertThat(mailSender.sent).noneMatch(m -> m.getTo()[0].equals("sanscopie@example.com"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void honeypotSilentlyDropsBots() {
        long before = content.inbox().size();
        content.submitContact("Robot", "bot@example.com", "spam", false, true,
                "http://spam.example", UUID.randomUUID().toString(), Locale.forLanguageTag("fr"));
        assertThat(content.inbox()).hasSize((int) before);
    }

    @Test
    void consentAndRequiredFieldsValidatedServerSide() {
        assertThatThrownBy(() -> content.submitContact("X", "x@example.com", "msg",
                false, false, "", UUID.randomUUID().toString(), Locale.forLanguageTag("fr")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("consent.required");
        assertThatThrownBy(() -> content.submitContact("", "x@example.com", "msg",
                false, true, "", UUID.randomUUID().toString(), Locale.forLanguageTag("fr")))
                .hasMessage("contact.fields");
        assertThatThrownBy(() -> content.submitContact("X", "pas-un-email", "msg",
                false, true, "", UUID.randomUUID().toString(), Locale.forLanguageTag("fr")))
                .hasMessage("email.invalid");
    }

    @Test
    void rateLimiterBlocksFloods() {
        String client = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            content.submitContact("Flood " + i, "flood@example.com", "msg " + i,
                    false, true, "", client, Locale.forLanguageTag("fr"));
        }
        assertThatThrownBy(() -> content.submitContact("Flood 6", "flood@example.com", "msg",
                false, true, "", client, Locale.forLanguageTag("fr")))
                .hasMessage("rate.limited");
    }
}
