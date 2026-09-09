package tn.civiccare.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.notifications.EmailComposer;
import tn.civiccare.notifications.EmailOutboxService;
import tn.civiccare.observability.Telemetry.ValidationException;
import tn.civiccare.shared.RateLimiter;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ContentService {

    public interface ContentPageRepository extends JpaRepository<ContentPage, UUID> {
        Optional<ContentPage> findBySlug(String slug);
    }

    public interface ContactMessageRepository extends JpaRepository<ContactMessage, UUID> {
        List<ContactMessage> findByStatusOrderByCreatedAtDesc(ContactMessage.Status status);

        List<ContactMessage> findAllByOrderByCreatedAtDesc();
    }

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private final ContentPageRepository pages;
    private final ContactMessageRepository contacts;
    private final EmailOutboxService emailOutbox;
    private final EmailComposer emails;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public ContentService(ContentPageRepository pages, ContactMessageRepository contacts,
                          EmailOutboxService emailOutbox, EmailComposer emails,
                          RateLimiter rateLimiter, Clock clock) {
        this.pages = pages;
        this.contacts = contacts;
        this.emailOutbox = emailOutbox;
        this.emails = emails;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<ContentPage> page(String slug) {
        return pages.findBySlug(slug);
    }

    @Transactional(readOnly = true)
    public List<ContentPage> allPages() {
        return pages.findAll();
    }

    /**
     * Réception d'un message de contact. Anti-spam discret : champ honeypot (doit rester vide,
     * choix CivicCare) + limitation de débit par client. Copie facultative via l'outbox.
     */
    @Transactional
    public void submitContact(String name, String email, String body, boolean copyRequested,
                              boolean consent, String honeypot, String clientKey, Locale locale) {
        if (honeypot != null && !honeypot.isBlank()) {
            // Robot probable : accepté silencieusement, rien n'est enregistré.
            return;
        }
        rateLimiter.check("contact:" + clientKey, 5, 3600);
        if (name == null || name.isBlank() || body == null || body.isBlank()) {
            throw new ValidationException("contact.fields");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new ValidationException("email.invalid");
        }
        if (!consent) {
            throw new ValidationException("consent.required");
        }
        ContactMessage message = new ContactMessage(UUID.randomUUID(), name.trim(), email.trim(),
                body.trim(), copyRequested, clock.instant());
        contacts.save(message);
        if (copyRequested) {
            emailOutbox.enqueueEmail("contact.copy", email.trim(),
                    emails.subject(locale, "email.contact.copy.subject"),
                    emails.contactCopyBody(locale, name.trim(), body.trim()),
                    "contact.copy:" + message.getId());
        }
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public List<ContactMessage> inbox() {
        return contacts.findAllByOrderByCreatedAtDesc();
    }

    @PreAuthorize("hasAnyRole('AGENT','MODERATOR','ADMIN')")
    @Transactional
    public void markProcessed(UUID messageId, UUID staffId) {
        contacts.findById(messageId).ifPresent(m -> m.markProcessed(staffId, clock.instant()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void updatePage(String slug, String titleFr, String titleAr, String titleEn,
                           String bodyFr, String bodyAr, String bodyEn, UUID by) {
        ContentPage page = pages.findBySlug(slug)
                .orElseThrow(() -> new ValidationException("page.unknown"));
        page.update(titleFr, titleAr, titleEn, bodyFr, bodyAr, bodyEn, by, clock.instant());
    }
}
