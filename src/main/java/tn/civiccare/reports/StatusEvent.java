package tn.civiccare.reports;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "status_event")
public class StatusEvent {

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private WorkflowStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private WorkflowStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StatusEvent() {
    }

    public StatusEvent(UUID id, UUID reportId, WorkflowStatus fromStatus, WorkflowStatus toStatus,
                       UUID actorId, String reason, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
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

    public WorkflowStatus getFromStatus() {
        return fromStatus;
    }

    public WorkflowStatus getToStatus() {
        return toStatus;
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
