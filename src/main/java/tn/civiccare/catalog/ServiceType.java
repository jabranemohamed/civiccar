package tn.civiccare.catalog;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "service_type")
public class ServiceType {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    private CategoryGroup group;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(name = "label_fr", nullable = false)
    private String labelFr;

    @Column(name = "label_ar", nullable = false)
    private String labelAr;

    @Column(name = "help_fr")
    private String helpFr;

    @Column(name = "help_ar")
    private String helpAr;

    @Column(name = "label_en")
    private String labelEn;

    @Column(name = "help_en")
    private String helpEn;

    /** false = la description standard n'est pas affichée (variante affiches politiques). */
    @Column(name = "standard_description", nullable = false)
    private boolean standardDescription = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sort;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_type_field",
            joinColumns = @JoinColumn(name = "service_type_id"),
            inverseJoinColumns = @JoinColumn(name = "field_definition_id"))
    @OrderBy("sort")
    private List<FieldDefinition> fields = new ArrayList<>();

    protected ServiceType() {
    }

    public UUID getId() {
        return id;
    }

    public CategoryGroup getGroup() {
        return group;
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

    public String help(Locale locale) {
        return switch (locale.getLanguage()) {
            case "ar" -> helpAr;
            case "en" -> helpEn != null ? helpEn : helpFr;
            default -> helpFr;
        };
    }

    public boolean isStandardDescription() {
        return standardDescription;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSort() {
        return sort;
    }

    public List<FieldDefinition> getFields() {
        return fields;
    }
}
