package tn.civiccare.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "category_group")
public class CategoryGroup {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "label_fr", nullable = false)
    private String labelFr;

    @Column(name = "label_ar", nullable = false)
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(nullable = false)
    private int sort;

    @Column(nullable = false)
    private boolean active = true;

    protected CategoryGroup() {
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getLabelFr() {
        return labelFr;
    }

    public String getLabelAr() {
        return labelAr;
    }

    /** Libellé localisé ; l'anglais replie sur le français s'il n'est pas renseigné. */
    public String label(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> labelAr;
            case "en" -> labelEn != null ? labelEn : labelFr;
            default -> labelFr;
        };
    }

    public int getSort() {
        return sort;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setLabelFr(String labelFr) {
        this.labelFr = labelFr;
    }

    public void setLabelAr(String labelAr) {
        this.labelAr = labelAr;
    }
}
