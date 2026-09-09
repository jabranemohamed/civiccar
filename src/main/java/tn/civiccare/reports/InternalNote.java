package tn.civiccare.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Note interne : jamais exposée publiquement, ni par l'API ni par l'UI citoyenne. */
@Entity
@Table(name = "internal_note")
public class InternalNote {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InternalNote() {
    }

    public InternalNote(UUID id, UUID reportId, UUID authorId, String body, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.authorId = authorId;
        this.body = body;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
