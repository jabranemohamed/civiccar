package tn.civiccare.subscriptions;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Abonnement e-mail aux mises à jour d'un dossier. Aucune notification avant activation. */
@Entity
@Table(name = "subscription")
public class Subscription {

    public enum Status {PENDING, ACTIVE, UNSUBSCRIBED}

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "unsubscribed_at")
    private Instant unsubscribedAt;

    protected Subscription() {
    }

    public Subscription(UUID id, UUID reportId, String email, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.email = email;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getEmail() {
        return email;
    }

    public Status getStatus() {
        return status;
    }

    public void activate(Instant now) {
        this.status = Status.ACTIVE;
        this.activatedAt = now;
    }

    public void unsubscribe(Instant now) {
        this.status = Status.UNSUBSCRIBED;
        this.unsubscribedAt = now;
    }
}
