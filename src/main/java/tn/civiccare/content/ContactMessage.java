package tn.civiccare.content;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Message du formulaire de contact : reçu dans la boîte interne, aucun destinataire réel préconfiguré. */
@Entity
@Table(name = "contact_message")
public class ContactMessage {

    public enum Status {NEW, PROCESSED}

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String body;

    @Column(name = "copy_requested", nullable = false)
    private boolean copyRequested;

    @Column(name = "consent_at", nullable = false)
    private Instant consentAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_by")
    private UUID processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected ContactMessage() {
    }

    public ContactMessage(UUID id, String name, String email, String body, boolean copyRequested, Instant now) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.body = body;
        this.copyRequested = copyRequested;
        this.consentAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getBody() {
        return body;
    }

    public boolean isCopyRequested() {
        return copyRequested;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markProcessed(UUID by, Instant when) {
        this.status = Status.PROCESSED;
        this.processedBy = by;
        this.processedAt = when;
    }
}
