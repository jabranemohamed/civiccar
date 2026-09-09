package tn.civiccare.notifications;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Message en attente d'envoi (e-mail). Écrit dans la même transaction que l'action métier. */
@Entity
@Table(name = "outbox_message")
public class OutboxMessage {

    public enum Status {PENDING, SENT, FAILED}

    @Id
    private UUID id;

    @Column(nullable = false)
    private String kind;

    /** Contenu de l'e-mail (to, subject, body, traceparent). Jamais journalisé en clair. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "dedup_key", unique = true)
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error")
    private String lastError;

    protected OutboxMessage() {
    }

    public OutboxMessage(UUID id, String kind, Map<String, String> payload, String dedupKey, Instant now) {
        this.id = id;
        this.kind = kind;
        this.payload = payload;
        this.dedupKey = dedupKey;
        this.createdAt = now;
        this.nextAttemptAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void markSent(Instant now) {
        this.status = Status.SENT;
        this.sentAt = now;
    }

    /** Reprise bornée avec backoff exponentiel ; FAILED après épuisement des essais. */
    public void markFailure(String error, Instant now, int maxAttempts) {
        this.attempts++;
        this.lastError = error == null ? "" : error.substring(0, Math.min(error.length(), 480));
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        } else {
            long delaySeconds = Math.min(3600, (long) Math.pow(2, attempts) * 30);
            this.nextAttemptAt = now.plusSeconds(delaySeconds);
        }
    }

    /** Purge de données personnelles dans un message non envoyé. */
    public void redactPayload(Map<String, String> redacted) {
        this.payload = redacted;
    }
}
