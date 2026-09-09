package tn.civiccare.moderation;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "moderation_decision")
public class ModerationDecision {

    public enum Decision {PUBLISH, HIDE, EDIT_PUBLIC_TEXT, REMOVE_MEDIA, APPROVE_MEDIA, CHANGE_CATEGORY}

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Decision decision;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModerationDecision() {
    }

    public ModerationDecision(UUID id, UUID reportId, Decision decision, UUID actorId, String reason, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.decision = decision;
        this.actorId = actorId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public Decision getDecision() {
        return decision;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
