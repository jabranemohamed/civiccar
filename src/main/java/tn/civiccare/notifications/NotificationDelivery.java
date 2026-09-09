package tn.civiccare.notifications;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Trace de livraison avec clé de déduplication : empêche les doublons applicatifs connus.
 * Limite documentée : si l'acquittement SMTP est perdu après envoi effectif, un
 * doublon est possible (exactement-une-fois impossible avec SMTP).
 */
@Entity
@Table(name = "notification_delivery")
public class NotificationDelivery {

    @Id
    private UUID id;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(nullable = false)
    private String kind;

    @Column(name = "dedup_key", nullable = false, unique = true)
    private String dedupKey;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationDelivery() {
    }

    public NotificationDelivery(UUID id, UUID subscriptionId, UUID reportId, String kind,
                                String dedupKey, String status, Instant createdAt) {
        this.id = id;
        this.subscriptionId = subscriptionId;
        this.reportId = reportId;
        this.kind = kind;
        this.dedupKey = dedupKey;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDedupKey() {
        return dedupKey;
    }
}
