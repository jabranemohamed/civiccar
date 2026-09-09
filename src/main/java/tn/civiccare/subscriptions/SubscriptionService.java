package tn.civiccare.subscriptions;

import io.opentelemetry.api.common.Attributes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.civiccare.observability.Telemetry;
import tn.civiccare.reports.Report;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Abonnements e-mail et suivis d'appareil. L'état d'abonnement n'est jamais public ;
 * aucun endpoint ne retourne les abonnements d'une adresse sans vérification d'accès.
 */
@Service
public class SubscriptionService {

    private static final Duration CONFIRM_TTL = Duration.ofDays(7);
    private static final Duration UNSUBSCRIBE_TTL = Duration.ofDays(365);

    private final SubscriptionRepository subscriptions;
    private final ActionTokenRepository tokens;
    private final BrowserIdentityRepository browsers;
    private final BookmarkRepository bookmarks;
    private final Telemetry telemetry;
    private final Clock clock;
    private final String baseUrl;

    public SubscriptionService(SubscriptionRepository subscriptions, ActionTokenRepository tokens,
                               BrowserIdentityRepository browsers, BookmarkRepository bookmarks,
                               Telemetry telemetry, Clock clock,
                               @Value("${civiccare.base-url:http://localhost:8080}") String baseUrl) {
        this.subscriptions = subscriptions;
        this.tokens = tokens;
        this.browsers = browsers;
        this.bookmarks = bookmarks;
        this.telemetry = telemetry;
        this.clock = clock;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    /**
     * Prépare un abonnement PENDING et retourne l'URL d'activation (jeton en clair dans
     * l'URL uniquement, hachage en base). Appelé dans la transaction de création du dossier.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String prepareSubscription(Report report, String email, Instant now) {
        Subscription sub = subscriptions.findByReportIdAndEmail(report.getId(), email)
                .orElseGet(() -> subscriptions.save(
                        new Subscription(UUID.randomUUID(), report.getId(), email, now)));
        String raw = Tokens.newToken();
        tokens.save(new ActionToken(UUID.randomUUID(), Tokens.hash(raw),
                ActionToken.Purpose.SUBSCRIPTION_CONFIRM, sub.getId(), now.plus(CONFIRM_TTL), now));
        return baseUrl + "/s/confirm/" + raw;
    }

    /** Demande d'abonnement depuis la fiche publique (consentement explicite requis en amont). */
    @Transactional
    public String requestSubscription(UUID reportId, String email) {
        Instant now = clock.instant();
        Subscription sub = subscriptions.findByReportIdAndEmail(reportId, email)
                .orElseGet(() -> subscriptions.save(new Subscription(UUID.randomUUID(), reportId, email, now)));
        if (sub.getStatus() == Subscription.Status.ACTIVE) {
            // Pas de divulgation : même comportement apparent qu'une création.
            return null;
        }
        String raw = Tokens.newToken();
        tokens.save(new ActionToken(UUID.randomUUID(), Tokens.hash(raw),
                ActionToken.Purpose.SUBSCRIPTION_CONFIRM, sub.getId(), now.plus(CONFIRM_TTL), now));
        return baseUrl + "/s/confirm/" + raw;
    }

    /** Activation par jeton : usage unique, expiration vérifiée, rejouable sans effet. */
    @Transactional
    public boolean confirm(String rawToken) {
        return telemetry.span("subscription.confirm", Attributes.empty(), () -> {
            Instant now = clock.instant();
            Optional<ActionToken> token = tokens.findByTokenHash(Tokens.hash(rawToken))
                    .filter(t -> t.getPurpose() == ActionToken.Purpose.SUBSCRIPTION_CONFIRM)
                    .filter(t -> t.isUsable(now));
            if (token.isEmpty()) {
                return false;
            }
            Subscription sub = subscriptions.findById(token.get().getSubscriptionId()).orElse(null);
            if (sub == null || sub.getStatus() == Subscription.Status.UNSUBSCRIBED) {
                return false;
            }
            sub.activate(now);
            token.get().markUsed(now);
            return true;
        });
    }

    @Transactional
    public boolean unsubscribe(String rawToken) {
        Instant now = clock.instant();
        Optional<ActionToken> token = tokens.findByTokenHash(Tokens.hash(rawToken))
                .filter(t -> t.getPurpose() == ActionToken.Purpose.SUBSCRIPTION_UNSUBSCRIBE)
                .filter(t -> t.isUsable(now));
        if (token.isEmpty()) {
            return false;
        }
        Subscription sub = subscriptions.findById(token.get().getSubscriptionId()).orElse(null);
        if (sub == null) {
            return false;
        }
        sub.unsubscribe(now);
        token.get().markUsed(now);
        return true;
    }

    /** Abonnés actifs d'un dossier avec URL de désinscription fraîche (jeton par envoi). */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ActiveSubscriber> activeSubscribers(UUID reportId) {
        Instant now = clock.instant();
        return subscriptions.findByReportIdAndStatus(reportId, Subscription.Status.ACTIVE).stream()
                .map(sub -> {
                    String raw = Tokens.newToken();
                    tokens.save(new ActionToken(UUID.randomUUID(), Tokens.hash(raw),
                            ActionToken.Purpose.SUBSCRIPTION_UNSUBSCRIBE, sub.getId(),
                            now.plus(UNSUBSCRIBE_TTL), now));
                    return new ActiveSubscriber(sub.getId(), sub.getEmail(),
                            baseUrl + "/s/unsubscribe/" + raw);
                })
                .toList();
    }

    public record ActiveSubscriber(UUID subscriptionId, String email, String unsubscribeUrl) {
    }

    // ===== Favoris d'appareil =====

    /** Résout ou crée l'identité navigateur à partir du jeton du cookie. */
    @Transactional
    public BrowserBinding resolveBrowser(String rawCookieToken) {
        Instant now = clock.instant();
        if (rawCookieToken != null && !rawCookieToken.isBlank()) {
            Optional<BrowserIdentity> existing = browsers.findByTokenHash(Tokens.hash(rawCookieToken));
            if (existing.isPresent()) {
                existing.get().touch(now);
                return new BrowserBinding(existing.get().getId(), rawCookieToken, false);
            }
        }
        String raw = Tokens.newToken();
        BrowserIdentity identity = browsers.save(new BrowserIdentity(UUID.randomUUID(), Tokens.hash(raw), now));
        return new BrowserBinding(identity.getId(), raw, true);
    }

    public record BrowserBinding(UUID browserId, String rawToken, boolean created) {
    }

    /** @return true si le dossier est désormais suivi, false s'il ne l'est plus. */
    @Transactional
    public boolean toggleBookmark(UUID browserId, UUID reportId) {
        Optional<Bookmark> existing = bookmarks.findByBrowserIdentityIdAndReportId(browserId, reportId);
        if (existing.isPresent()) {
            bookmarks.delete(existing.get());
            return false;
        }
        bookmarks.save(new Bookmark(UUID.randomUUID(), browserId, reportId, clock.instant()));
        return true;
    }

    @Transactional(readOnly = true)
    public boolean isBookmarked(UUID browserId, UUID reportId) {
        return bookmarks.findByBrowserIdentityIdAndReportId(browserId, reportId).isPresent();
    }

    @Transactional(readOnly = true)
    public List<UUID> bookmarkedReportIds(UUID browserId) {
        return bookmarks.findByBrowserIdentityIdOrderByCreatedAtDesc(browserId).stream()
                .map(Bookmark::getReportId)
                .toList();
    }

    /**
     * Purge liée à un dossier : jetons puis abonnements du dossier sont SUPPRIMÉS
     * (vider l'e-mail violerait l'unicité (report_id, email) avec plusieurs abonnés).
     * Les abonnements de la même adresse sur d'autres dossiers ne sont pas touchés.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void purgeForReport(UUID reportId) {
        for (Subscription sub : subscriptions.findByReportId(reportId)) {
            tokens.deleteBySubscriptionId(sub.getId());
            subscriptions.delete(sub);
        }
    }
}
