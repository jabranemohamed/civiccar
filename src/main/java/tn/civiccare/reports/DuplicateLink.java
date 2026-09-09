package tn.civiccare.reports;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Doublon confirmé par un agent : lien vers le dossier canonique, sans boucle (contrainte SQL). */
@Entity
@Table(name = "duplicate_link")
public class DuplicateLink {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false, unique = true)
    private UUID reportId;

    @Column(name = "canonical_id", nullable = false)
    private UUID canonicalId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DuplicateLink() {
    }

    public DuplicateLink(UUID id, UUID reportId, UUID canonicalId, UUID createdBy, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.canonicalId = canonicalId;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public UUID getCanonicalId() {
        return canonicalId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
