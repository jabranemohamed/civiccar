package tn.civiccare.subscriptions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Identité navigateur pour les favoris « sur cet appareil ». Le jeton est posé en cookie
 * sécurisé et seul son hachage est stocké. Ne prouve pas l'identité d'un déclarant et ne
 * donne aucun accès aux dossiers privés.
 */
@Entity
@Table(name = "browser_identity")
public class BrowserIdentity {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected BrowserIdentity() {
    }

    public BrowserIdentity(UUID id, String tokenHash, Instant now) {
        this.id = id;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public UUID getId() {
        return id;
    }

    public void touch(Instant now) {
        this.lastSeenAt = now;
    }
}
