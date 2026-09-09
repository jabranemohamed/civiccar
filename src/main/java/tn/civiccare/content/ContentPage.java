package tn.civiccare.content;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Page de contenu éditoriale (FAQ, conditions, confidentialité…), FR/AR, éditable en back-office. */
@Entity
@Table(name = "content_page")
public class ContentPage {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "title_fr", nullable = false)
    private String titleFr;

    @Column(name = "title_ar", nullable = false)
    private String titleAr;

    @Column(name = "body_fr", nullable = false)
    private String bodyFr;

    @Column(name = "body_ar", nullable = false)
    private String bodyAr;

    @Column(name = "title_en")
    private String titleEn;

    @Column(name = "body_en")
    private String bodyEn;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected ContentPage() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    /** Titre/corps localisés ; l'anglais replie sur le français s'il n'est pas renseigné. */
    public String title(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> titleAr;
            case "en" -> titleEn != null ? titleEn : titleFr;
            default -> titleFr;
        };
    }

    public String body(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> bodyAr;
            case "en" -> bodyEn != null ? bodyEn : bodyFr;
            default -> bodyFr;
        };
    }

    public String getTitleFr() {
        return titleFr;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public String getBodyFr() {
        return bodyFr;
    }

    public String getBodyAr() {
        return bodyAr;
    }

    public void update(String titleFr, String titleAr, String titleEn,
                       String bodyFr, String bodyAr, String bodyEn, UUID by, Instant when) {
        this.titleFr = titleFr;
        this.titleAr = titleAr;
        this.titleEn = titleEn == null || titleEn.isBlank() ? null : titleEn;
        this.bodyFr = bodyFr;
        this.bodyAr = bodyAr;
        this.bodyEn = bodyEn == null || bodyEn.isBlank() ? null : bodyEn;
        this.updatedBy = by;
        this.updatedAt = when;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public String getBodyEn() {
        return bodyEn;
    }
}
