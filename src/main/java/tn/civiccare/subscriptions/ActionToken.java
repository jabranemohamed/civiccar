package tn.civiccare.subscriptions;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Jeton d'action : forte entropie, haché en base (jamais stocké en clair), expirable,
 * une seule finalité, usage unique. Aucun e-mail dans les URLs.
 */
@Entity
@Table(name = "action_token")
public class ActionToken {

    public enum Purpose {SUBSCRIPTION_CONFIRM, SUBSCRIPTION_UNSUBSCRIBE}

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Purpose purpose;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ActionToken() {
    }

    public ActionToken(UUID id, String tokenHash, Purpose purpose, UUID subscriptionId,
                       Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.purpose = purpose;
        this.subscriptionId = subscriptionId;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }
}
