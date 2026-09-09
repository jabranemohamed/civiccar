package tn.civiccare.subscriptions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bookmark")
public class Bookmark {

    @Id
    private UUID id;

    @Column(name = "browser_identity_id", nullable = false)
    private UUID browserIdentityId;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Bookmark() {
    }

    public Bookmark(UUID id, UUID browserIdentityId, UUID reportId, Instant createdAt) {
        this.id = id;
        this.browserIdentityId = browserIdentityId;
        this.reportId = reportId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }
}
