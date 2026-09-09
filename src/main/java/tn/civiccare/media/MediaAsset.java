package tn.civiccare.media;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Photo d'un signalement. Privée par défaut (PENDING) ; seuls les dérivés approuvés
 * (réencodés, EXIF supprimé) sont publiés.
 */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    public enum ModerationStatus {PENDING, APPROVED, REMOVED}

    @Id
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "thumb_key")
    private String thumbKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", nullable = false)
    private ModerationStatus moderationStatus = ModerationStatus.PENDING;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MediaAsset() {
    }

    public MediaAsset(UUID id, UUID reportId, String storageKey, String thumbKey, String contentType,
                      long sizeBytes, int width, int height, int sort, Instant createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.storageKey = storageKey;
        this.thumbKey = thumbKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.width = width;
        this.height = height;
        this.sort = sort;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getThumbKey() {
        return thumbKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public ModerationStatus getModerationStatus() {
        return moderationStatus;
    }

    public void setModerationStatus(ModerationStatus moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    public int getSort() {
        return sort;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
