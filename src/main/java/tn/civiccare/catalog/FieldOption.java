package tn.civiccare.catalog;

import jakarta.persistence.*;

import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "field_option")
public class FieldOption {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_definition_id")
    private FieldDefinition field;

    @Column(nullable = false)
    private String code;

    @Column(name = "label_fr", nullable = false)
    private String labelFr;

    @Column(name = "label_ar", nullable = false)
    private String labelAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sort;

    protected FieldOption() {
    }

    public UUID getId() {
        return id;
    }

    public FieldDefinition getField() {
        return field;
    }

    public String getCode() {
        return code;
    }

    public String label(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> labelAr;
            case "en" -> labelEn != null ? labelEn : labelFr;
            default -> labelFr;
        };
    }

    public String getLabelFr() {
        return labelFr;
    }

    public String getLabelAr() {
        return labelAr;
    }

    public boolean isActive() {
        return active;
    }

    public int getSort() {
        return sort;
    }
}
